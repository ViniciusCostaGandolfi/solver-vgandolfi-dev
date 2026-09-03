import logging
import math
import random
from typing import Dict, List, Optional, Set, Tuple

import numpy as np

from app.algorithms.vrp.tsp_solver import solve_tsp_ortools
from app.dtos import Address, Client, Coordinate, RouteDto, VehicleType, VrpIn
from app.services.osrm_service import osrm_service

logger = logging.getLogger(__name__)


class ALNSRoute:
    """Lightweight route representation using client indices for fast ALNS iteration.

    Client indices are 1..N (origin is 0). Distances are looked up in the
    precomputed distance matrix.
    """

    def __init__(
        self,
        client_indices: List[int],
        dist_matrix: np.ndarray,
        vrp_in: VrpIn,
        vehicle: VehicleType,
    ) -> None:
        self.client_indices = list(client_indices)
        self.dist_matrix = dist_matrix
        self.vrp_in = vrp_in
        self.vehicle = vehicle
        self.distance = 0.0
        self.volume = 0.0
        self.weight = 0.0
        self._update_stats()

    def _update_stats(self) -> None:
        self.distance = 0.0
        self.volume = 0.0
        self.weight = 0.0
        if not self.client_indices:
            return

        prev_node = 0
        for client_idx in self.client_indices:
            self.distance += self.dist_matrix[prev_node, client_idx]
            prev_node = client_idx
            client = self.vrp_in.clients[client_idx - 1]
            self.volume += client.volume_liters
            self.weight += client.weight_kg

        self.distance += self.dist_matrix[prev_node, 0]

    def insert_client(self, client_idx: int, position: int) -> bool:
        if not self.can_insert(client_idx, position):
            return False
        self.client_indices.insert(position, client_idx)
        self._update_stats()
        return True

    def remove_client(self, client_idx: int) -> None:
        if client_idx in self.client_indices:
            self.client_indices.remove(client_idx)
            self._update_stats()

    def can_insert(self, client_idx: int, position: Optional[int] = None) -> bool:
        client = self.vrp_in.clients[client_idx - 1]
        if self.vehicle.max_deliveries is not None and len(self.client_indices) + 1 > self.vehicle.max_deliveries:
            return False
        if self.vehicle.max_volume_liters is not None and self.volume + client.volume_liters > self.vehicle.max_volume_liters:
            return False
        if self.vehicle.max_weight_kg is not None and self.weight + client.weight_kg > self.vehicle.max_weight_kg:
            return False
        if self.vehicle.max_distance_meters is not None and position is not None:
            cost = self.get_insertion_cost(client_idx, position)
            if self.distance + cost > self.vehicle.max_distance_meters:
                return False
        return True

    def get_insertion_cost(self, client_idx: int, position: int) -> float:
        prev_node = 0 if position == 0 else self.client_indices[position - 1]
        next_node = 0 if position == len(self.client_indices) else self.client_indices[position]
        dist_before = self.dist_matrix[prev_node, next_node]
        dist_after = self.dist_matrix[prev_node, client_idx] + self.dist_matrix[client_idx, next_node]
        return dist_after - dist_before

    def get_best_insertion(self, client_idx: int) -> Tuple[int, float]:
        if not self.can_insert(client_idx):
            return -1, float("inf")
        best_cost = float("inf")
        best_pos = -1
        for i in range(len(self.client_indices) + 1):
            cost = self.get_insertion_cost(client_idx, i)
            within_distance = (
                self.vehicle.max_distance_meters is None
                or self.distance + cost <= self.vehicle.max_distance_meters
            )
            if within_distance and cost < best_cost:
                best_cost = cost
                best_pos = i
        return best_pos, best_cost

    def get_removal_savings(self, client_idx: int) -> float:
        if client_idx not in self.client_indices:
            return 0.0
        position = self.client_indices.index(client_idx)
        prev_node = 0 if position == 0 else self.client_indices[position - 1]
        next_node = 0 if position == len(self.client_indices) - 1 else self.client_indices[position + 1]
        dist_before = self.dist_matrix[prev_node, client_idx] + self.dist_matrix[client_idx, next_node]
        dist_after = self.dist_matrix[prev_node, next_node]
        return dist_before - dist_after

    def clone(self) -> "ALNSRoute":
        return ALNSRoute(self.client_indices, self.dist_matrix, self.vrp_in, self.vehicle)


class ALNSState:
    """Represents a full VRP solution state during ALNS search."""

    def __init__(self, routes: List[ALNSRoute], unassigned: Set[int], vrp_in: VrpIn) -> None:
        self.routes = routes
        self.unassigned = set(unassigned)
        self.vrp_in = vrp_in
        self.cost = self.calculate_cost()

    def calculate_cost(self) -> float:
        cost = sum(
            r.distance + (r.vehicle.fixed_cost if r.vehicle.fixed_cost else 0)
            for r in self.routes
            if r.client_indices
        )
        cost += len(self.unassigned) * 1_000_000

        used_counts: Dict[str, int] = {}
        for v in self.vrp_in.vehicles:
            used_counts[str(v.id)] = 0
        for r in self.routes:
            if r.client_indices:
                used_counts[str(r.vehicle.id)] += 1
        for v in self.vrp_in.vehicles:
            vid = str(v.id)
            if v.max_routes is not None and used_counts[vid] > v.max_routes:
                cost += (used_counts[vid] - v.max_routes) * 500_000
            if v.min_routes > 0 and used_counts[vid] < v.min_routes:
                cost += (v.min_routes - used_counts[vid]) * 500_000
        return cost

    def update_cost(self) -> None:
        self.cost = self.calculate_cost()

    def clone(self) -> "ALNSState":
        return ALNSState([r.clone() for r in self.routes], self.unassigned, self.vrp_in)


class ALNSSolver:
    """Adaptive Large Neighbourhood Search for VRP refinement."""

    def __init__(
        self,
        initial_routes: List[RouteDto],
        vrp_in: VrpIn,
        dist_matrix: np.ndarray,
        is_southeast: bool = False,
    ) -> None:
        self.vrp_in = vrp_in
        self.dist_matrix = dist_matrix
        self.is_southeast = is_southeast

        self.client_id_to_idx = {client.id: i + 1 for i, client in enumerate(vrp_in.clients)}
        self.idx_to_client = {i + 1: client for i, client in enumerate(vrp_in.clients)}

        vehicle_map = {v.id: v for v in vrp_in.vehicles}
        alns_routes = []
        for route in initial_routes:
            client_indices = [self.client_id_to_idx[c.id] for c in route.clients if c.id in self.client_id_to_idx]
            veh = vehicle_map.get(route.vehicle_id) if route.vehicle_id else vrp_in.vehicles[0]
            alns_routes.append(ALNSRoute(client_indices, self.dist_matrix, self.vrp_in, veh))

        self.best_state = ALNSState(alns_routes, set(), self.vrp_in)
        self.current_state = self.best_state.clone()

        # Adaptive operator weights
        self.destroy_ops = [self._destroy_random, self._destroy_worst, self._destroy_related]
        self.repair_ops = [self._repair_greedy, self._repair_regret]

        self.destroy_weights = [1.0] * len(self.destroy_ops)
        self.repair_weights = [1.0] * len(self.repair_ops)
        self.destroy_scores = [0.0] * len(self.destroy_ops)
        self.repair_scores = [0.0] * len(self.repair_ops)
        self.destroy_uses = [0] * len(self.destroy_ops)
        self.repair_uses = [0] * len(self.repair_ops)

    @staticmethod
    def _select_operator(weights: List[float]) -> int:
        total = sum(weights)
        probs = [w / total for w in weights]
        return np.random.choice(len(weights), p=probs)

    # --- Destroy Operators ---

    def _destroy_random(self, state: ALNSState, num_to_remove: int) -> None:
        assigned_clients = [
            (r_idx, c_idx)
            for r_idx, route in enumerate(state.routes)
            for c_idx in route.client_indices
        ]
        if not assigned_clients:
            return
        to_remove = random.sample(assigned_clients, min(num_to_remove, len(assigned_clients)))
        for r_idx, c_idx in to_remove:
            state.routes[r_idx].remove_client(c_idx)
            state.unassigned.add(c_idx)

    def _destroy_worst(self, state: ALNSState, num_to_remove: int) -> None:
        savings = []
        for r_idx, route in enumerate(state.routes):
            for c_idx in route.client_indices:
                saving = route.get_removal_savings(c_idx)
                savings.append((saving, r_idx, c_idx))
        savings.sort(reverse=True, key=lambda x: x[0])
        pool = savings[: min(len(savings), int(num_to_remove * 1.5))]
        to_remove = random.sample(pool, min(num_to_remove, len(pool)))
        for _, r_idx, c_idx in to_remove:
            state.routes[r_idx].remove_client(c_idx)
            state.unassigned.add(c_idx)

    def _destroy_related(self, state: ALNSState, num_to_remove: int) -> None:
        assigned_clients = []
        client_to_route: Dict[int, int] = {}
        for r_idx, route in enumerate(state.routes):
            for c_idx in route.client_indices:
                assigned_clients.append(c_idx)
                client_to_route[c_idx] = r_idx
        if not assigned_clients:
            return

        seed_idx = random.choice(assigned_clients)
        state.routes[client_to_route[seed_idx]].remove_client(seed_idx)
        state.unassigned.add(seed_idx)
        assigned_clients.remove(seed_idx)

        seed_client = self.idx_to_client[seed_idx]
        max_dist = float(np.max(self.dist_matrix)) if np.max(self.dist_matrix) > 0 else 1.0
        max_vol = max(c.volume_liters for c in self.vrp_in.clients) if self.vrp_in.clients else 1.0
        max_wgt = max(c.weight_kg for c in self.vrp_in.clients) if self.vrp_in.clients else 1.0

        relatedness: List[Tuple[float, int]] = []
        for c_idx in assigned_clients:
            c_client = self.idx_to_client[c_idx]
            dist = self.dist_matrix[seed_idx, c_idx] / max_dist
            vol_diff = abs(seed_client.volume_liters - c_client.volume_liters) / max_vol
            wgt_diff = abs(seed_client.weight_kg - c_client.weight_kg) / max_wgt
            affinity = dist + 0.5 * vol_diff + 0.5 * wgt_diff
            relatedness.append((affinity, c_idx))
        relatedness.sort(key=lambda x: x[0])
        pool = relatedness[: min(len(relatedness), int(num_to_remove * 1.5))]
        to_remove = random.sample(pool, min(num_to_remove - 1, len(pool)))
        for _, c_idx in to_remove:
            state.routes[client_to_route[c_idx]].remove_client(c_idx)
            state.unassigned.add(c_idx)

    # --- Repair Operators ---

    def _repair_greedy(self, state: ALNSState) -> None:
        unassigned_list = list(state.unassigned)
        random.shuffle(unassigned_list)
        while unassigned_list:
            best_client = -1
            best_route_idx = -1
            best_pos = -1
            best_cost = float("inf")
            for c_idx in unassigned_list:
                for r_idx, route in enumerate(state.routes):
                    pos, cost = route.get_best_insertion(c_idx)
                    if cost != float("inf"):
                        if not route.client_indices:
                            cost += route.vehicle.fixed_cost if route.vehicle.fixed_cost else 1000
                        if cost < best_cost:
                            best_cost = cost
                            best_route_idx = r_idx
                            best_pos = pos
                            best_client = c_idx
            if best_client != -1:
                state.routes[best_route_idx].insert_client(best_client, best_pos)
                state.unassigned.remove(best_client)
                unassigned_list.remove(best_client)
            else:
                break

    def _repair_regret(self, state: ALNSState) -> None:
        unassigned_list = list(state.unassigned)
        while unassigned_list:
            best_client = -1
            max_regret = -float("inf")
            best_route_idx = -1
            best_pos = -1
            for c_idx in unassigned_list:
                insertions = []
                for r_idx, route in enumerate(state.routes):
                    pos, cost = route.get_best_insertion(c_idx)
                    if cost != float("inf"):
                        if not route.client_indices:
                            cost += route.vehicle.fixed_cost if route.vehicle.fixed_cost else 1000
                        insertions.append((cost, r_idx, pos))
                if not insertions:
                    continue
                insertions.sort(key=lambda x: x[0])
                cost_best = insertions[0][0]
                cost_second = insertions[1][0] if len(insertions) > 1 else cost_best * 2
                regret = cost_second - cost_best
                if regret > max_regret:
                    max_regret = regret
                    best_client = c_idx
                    best_route_idx = insertions[0][1]
                    best_pos = insertions[0][2]
            if best_client != -1:
                state.routes[best_route_idx].insert_client(best_client, best_pos)
                state.unassigned.remove(best_client)
                unassigned_list.remove(best_client)
            else:
                break

    # --- Fleet Optimisation ---

    @staticmethod
    def _optimize_fleet(state: ALNSState, vrp_in: VrpIn) -> None:
        used_counts: Dict[str, int] = {}
        for v in vrp_in.vehicles:
            used_counts[str(v.id)] = 0
        for route in state.routes:
            if route.client_indices:
                used_counts[str(route.vehicle.id)] += 1

        sorted_vehicles = sorted(vrp_in.vehicles, key=lambda v: (v.fixed_cost, v.max_volume_liters or 0))

        for route in state.routes:
            if not route.client_indices:
                continue
            current_v = route.vehicle
            best_v = current_v
            for v in sorted_vehicles:
                if (v.fixed_cost if v.fixed_cost else 0) >= (current_v.fixed_cost if current_v.fixed_cost else 0):
                    break
                fits = (
                    (v.max_deliveries is None or len(route.client_indices) <= v.max_deliveries)
                    and (v.max_volume_liters is None or route.volume <= v.max_volume_liters)
                    and (v.max_weight_kg is None or route.weight <= v.max_weight_kg)
                    and (v.max_distance_meters is None or route.distance <= v.max_distance_meters)
                    and (v.max_routes is None or used_counts[str(v.id)] < v.max_routes)
                )
                if fits:
                    best_v = v
                    break
            if best_v != current_v:
                used_counts[str(current_v.id)] -= 1
                used_counts[str(best_v.id)] += 1
                route.vehicle = best_v

    # --- Main ALNS Loop ---

    def solve(
        self, iterations: int = 1000, initial_temp: float = 100.0, cooling_rate: float = 0.995
    ) -> Tuple[List[RouteDto], int, bool]:
        temp = initial_temp
        num_clients = len(self.vrp_in.clients)

        for iteration in range(iterations):
            state = self.current_state.clone()
            degree = max(2, int(num_clients * random.uniform(0.1, 0.4)))

            d_idx = self._select_operator(self.destroy_weights)
            r_idx = self._select_operator(self.repair_weights)

            self.destroy_ops[d_idx](state, degree)
            self.repair_ops[r_idx](state)
            self._optimize_fleet(state, self.vrp_in)
            state.update_cost()

            # Acceptance criteria (Simulated Annealing)
            accepted = False
            reward = 0.0
            if state.cost < self.best_state.cost:
                self.best_state = state.clone()
                self.current_state = state.clone()
                accepted = True
                reward = 1.5
            elif state.cost < self.current_state.cost:
                self.current_state = state.clone()
                accepted = True
                reward = 1.2
            else:
                delta = state.cost - self.current_state.cost
                prob = math.exp(-delta / max(temp, 1e-8))
                if random.random() < prob:
                    self.current_state = state.clone()
                    accepted = True
                    reward = 0.8
                else:
                    reward = 0.1

            # Update adaptive weights
            self.destroy_uses[d_idx] += 1
            self.repair_uses[r_idx] += 1
            self.destroy_scores[d_idx] += reward
            self.repair_scores[r_idx] += reward

            if (iteration + 1) % 100 == 0:
                decay = 0.1
                for j in range(len(self.destroy_weights)):
                    if self.destroy_uses[j] > 0:
                        self.destroy_weights[j] = self.destroy_weights[j] * (1 - decay) + decay * (
                            self.destroy_scores[j] / self.destroy_uses[j]
                        )
                    self.destroy_scores[j] = 0.0
                    self.destroy_uses[j] = 0
                for j in range(len(self.repair_weights)):
                    if self.repair_uses[j] > 0:
                        self.repair_weights[j] = self.repair_weights[j] * (1 - decay) + decay * (
                            self.repair_scores[j] / self.repair_uses[j]
                        )
                    self.repair_scores[j] = 0.0
                    self.repair_uses[j] = 0

            temp *= cooling_rate

            if (iteration + 1) % 500 == 0:
                logger.info(
                    f"ALNS iter {iteration+1}, best cost={self.best_state.cost:.2f}, "
                    f"unassigned={len(self.best_state.unassigned)}"
                )

        # Check fleet validity
        fleet_valid = True
        used_counts: Dict[str, int] = {}
        for v in self.vrp_in.vehicles:
            used_counts[str(v.id)] = 0
        for r in self.best_state.routes:
            if r.client_indices:
                used_counts[str(r.vehicle.id)] += 1
        for v in self.vrp_in.vehicles:
            vid = str(v.id)
            if v.max_routes is not None and used_counts[vid] > v.max_routes:
                fleet_valid = False
            if v.min_routes > 0 and used_counts[vid] < v.min_routes:
                fleet_valid = False

        return self._map_to_dto(self.best_state), len(self.best_state.unassigned), fleet_valid

    # --- Output Mapping ---

    def _map_to_dto(self, state: ALNSState) -> List[RouteDto]:
        result: List[RouteDto] = []
        for alns_route in state.routes:
            if not alns_route.client_indices:
                continue

            clients = [self.idx_to_client[idx] for idx in alns_route.client_indices]

            # Compute final route geometry
            if self.is_southeast:
                points_tsp = [(self.vrp_in.origin.latitude, self.vrp_in.origin.longitude)]
                for c in clients:
                    points_tsp.append((c.address.latitude, c.address.longitude))
                points_tsp.append((self.vrp_in.origin.latitude, self.vrp_in.origin.longitude))

                route_line_coords, total_distance = osrm_service.get_route(points_tsp)
                if route_line_coords:
                    route_line = [Coordinate(lat=p["lat"], lng=p["lng"]) for p in route_line_coords]
                    final_distance = total_distance
                else:
                    route_line = [Coordinate(lat=self.vrp_in.origin.latitude, lng=self.vrp_in.origin.longitude)]
                    for c in clients:
                        route_line.append(Coordinate(lat=c.address.latitude, lng=c.address.longitude))
                    route_line.append(Coordinate(lat=self.vrp_in.origin.latitude, lng=self.vrp_in.origin.longitude))
                    final_distance = alns_route.distance
            else:
                route_line = [Coordinate(lat=self.vrp_in.origin.latitude, lng=self.vrp_in.origin.longitude)]
                for c in clients:
                    route_line.append(Coordinate(lat=c.address.latitude, lng=c.address.longitude))
                route_line.append(Coordinate(lat=self.vrp_in.origin.latitude, lng=self.vrp_in.origin.longitude))
                final_distance = alns_route.distance

            result.append(
                RouteDto(
                    distance_meters=final_distance,
                    route_line=route_line,
                    vehicle_id=alns_route.vehicle.id,
                    vehicle_name=alns_route.vehicle.name,
                    clients=clients,
                    volume_liters=alns_route.volume,
                    weight_kg=alns_route.weight,
                    route_deliveries=len(clients),
                )
            )
        return result
