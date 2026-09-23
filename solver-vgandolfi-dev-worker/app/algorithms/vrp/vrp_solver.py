import logging
import time
from typing import Dict, List, Optional

import numpy as np
from haversine import Unit, haversine
from sklearn.cluster import KMeans

from app.algorithms.vrp.alns_solver import ALNSSolver
from app.algorithms.vrp.capacited_kmeans import CapacitedKMeans
from app.algorithms.vrp.tsp_solver import solve_tsp_ortools
from app.config import Settings
from app.dtos import Coordinate, RouteDto, VehicleType, VrpIn, VrpOut
from app.exceptions import InfeasibleVrpError
from app.services.osrm_service import osrm_service
import uuid

logger = logging.getLogger(__name__)

# Southeast region states for OSRM optimisation
_SOUTHEAST_STATES = {"SP", "RJ", "MG", "ES"}


def _check_southeast_region(vrp_in: VrpIn) -> bool:
    """Check if all addresses (origin + clients) are in the Southeast region."""
    if vrp_in.origin.state.upper() not in _SOUTHEAST_STATES:
        return False
    for client in vrp_in.clients:
        if client.address.state.upper() not in _SOUTHEAST_STATES:
            return False
    logger.info("VRP: All points in Southeast region — OSRM enabled.")
    return True


class VehicleRoutineProblemn:
    """VRP solver using KMeans clustering + ALNS refinement + TSP per route.

    This is the primary solver used for medium-sized instances.
    """

    def __init__(self, vrp_in: VrpIn, settings: Settings | None = None) -> None:
        self.vrp_in = vrp_in
        self.settings = settings
        self.routes: List[RouteDto] = []
        self.points = np.array(
            [[client.address.latitude, client.address.longitude] for client in vrp_in.clients]
        )
        self.volumes = np.array([client.volume_liters for client in vrp_in.clients])
        self.weights = np.array([client.weight_kg for client in vrp_in.clients])
        self.number_of_centroides = self._calculate_number_of_routes()
        self.is_southeast = _check_southeast_region(vrp_in)
        self.distances: np.ndarray = np.array([])
        self.route_points: List[int] = []
        self.route_volumes: List[float] = []
        self.vehicle_by_centroid: Dict[int, uuid.UUID] = {}

    def _calculate_number_of_routes(self) -> int:
        n_clients = len(self.vrp_in.clients)
        if n_clients == 0:
            return 0

        vols = [v.max_volume_liters for v in self.vrp_in.vehicles if v.max_volume_liters is not None]
        weights = [v.max_weight_kg for v in self.vrp_in.vehicles if v.max_weight_kg is not None]
        deliveries = [v.max_deliveries for v in self.vrp_in.vehicles if v.max_deliveries is not None]

        max_vol = max(vols) if vols else None
        max_weight = max(weights) if weights else None
        max_deliveries = max(deliveries) if deliveries else None

        # Validate individual client demands don't exceed the largest vehicle.
        # The fleet is expanded to cover the total demand, so only a single
        # client that cannot fit in any vehicle makes the instance infeasible.
        for client in self.vrp_in.clients:
            if max_vol is not None and client.volume_liters > max_vol:
                raise InfeasibleVrpError(
                    f"Client {client.id} volume ({client.volume_liters}L) exceeds max vehicle capacity ({max_vol}L)"
                )
            if max_weight is not None and client.weight_kg > max_weight:
                raise InfeasibleVrpError(
                    f"Client {client.id} weight ({client.weight_kg}kg) exceeds max vehicle capacity ({max_weight}kg)"
                )
            if max_deliveries is not None and max_deliveries < 1:
                raise InfeasibleVrpError(
                    f"Client {client.id} requires 1 delivery but max vehicle deliveries is {max_deliveries}"
                )

        cluster_candidates = []
        if max_vol is not None and self.volumes.sum() > 0:
            cluster_candidates.append(np.ceil(self.volumes.sum() / max_vol))
        if max_weight is not None and self.weights.sum() > 0:
            cluster_candidates.append(np.ceil(self.weights.sum() / max_weight))
        if max_deliveries is not None:
            cluster_candidates.append(np.ceil(n_clients / max_deliveries))

        capacity_base = int(max(cluster_candidates)) if cluster_candidates else 1

        if self.vrp_in.target_routes is not None:
            # Aim for the requested route count (never below what capacity needs).
            n_clusters = max(capacity_base, int(self.vrp_in.target_routes))
        else:
            # No target: slightly over-provision clusters to leave room for ALNS.
            n_clusters = int(capacity_base * 1.3)

        # KMeans cannot produce more clusters than points.
        return max(1, min(n_clusters, n_clients))

    def _calculate_centroids(self) -> np.ndarray:
        kmeans = KMeans(n_clusters=self.number_of_centroides, max_iter=1000, n_init=100, random_state=42)
        kmeans.fit(self.points)
        return kmeans.cluster_centers_

    def _calculate_distance_matrix(self) -> np.ndarray:
        centroids = self._calculate_centroids()
        n_clients = len(self.points)
        n_centroids = len(centroids)
        self.distances = np.zeros((n_clients, n_centroids))

        # OSRM Table for distance matrix if in Southeast
        if self.is_southeast and n_clients > 0:
            all_points = [tuple(p) for p in self.points] + [tuple(c) for c in centroids]
            sources = list(range(n_clients))
            destinations = list(range(n_clients, n_clients + n_centroids))
            coords_str = ";".join(f"{lng},{lat}" for lat, lng in all_points)
            src_str = ";".join(map(str, sources))
            dst_str = ";".join(map(str, destinations))
            url = f"{osrm_service.base_url}/table/v1/driving/{coords_str}?sources={src_str}&destinations={dst_str}&annotations=distance"

            try:
                import requests
                res = requests.get(url, timeout=30, verify=osrm_service.verify_ssl)
                res.raise_for_status()
                data = res.json()
                if "distances" in data:
                    self.distances = np.array(data["distances"])
                    return self.distances
            except Exception as e:
                logger.warning(f"OSRM Table failed for centroids: {e}. Falling back to haversine.")

        # Haversine fallback
        for i in range(n_clients):
            for j in range(n_centroids):
                self.distances[i, j] = haversine(self.points[i], centroids[j], Unit.METERS)

        return self.distances

    def _calculate_full_distance_matrix(self) -> np.ndarray:
        n = len(self.vrp_in.clients) + 1
        dist_matrix = np.zeros((n, n), dtype=np.float64)
        points = [(self.vrp_in.origin.latitude, self.vrp_in.origin.longitude)]
        for client in self.vrp_in.clients:
            points.append((client.address.latitude, client.address.longitude))

        if self.is_southeast:
            osrm_matrix = osrm_service.get_distance_matrix(points)
            if osrm_matrix is not None:
                return osrm_matrix

        for i in range(n):
            for j in range(n):
                if i != j:
                    dist_matrix[i, j] = haversine(points[i], points[j], Unit.METERS)
                else:
                    dist_matrix[i, j] = np.inf
        return dist_matrix

    def _calculate_routes_groups(self, route_offset: int = 0) -> int:
        self._calculate_distance_matrix()

        capacited_kmeans = CapacitedKMeans(
            self.points,
            self.volumes,
            self.distances,
            self.number_of_centroides,
            self.vrp_in.vehicles,
            self.vrp_in.target_routes,
            self.weights,
            route_offset,
        )
        status = capacited_kmeans.resolve()
        self.route_points = capacited_kmeans.route_points
        self.route_volumes = capacited_kmeans.route_volumes
        self.vehicle_by_centroid = capacited_kmeans.vehicle_by_centroid
        return status

    def resolve(self, deadline: Optional[float] = None) -> List[RouteDto]:
        max_attempts = 10
        attempts = 0
        best_alns_routes: Optional[List[RouteDto]] = None
        route_offset = 0

        while attempts < max_attempts:
            # Orçamento global de tempo: para com a melhor solução encontrada.
            if deadline is not None and time.monotonic() > deadline:
                logger.warning(
                    f"VehicleRoutineProblemn: time budget exceeded "
                    f"(attempt {attempts}); returning best solution so far"
                )
                break

            status = self._calculate_routes_groups(route_offset)
            if status == 0:
                attempts += 1
                self.number_of_centroides = int(np.ceil(self.number_of_centroides + 1))
                logger.info(
                    f"KMeans infeasible. Increasing centroids to {self.number_of_centroides} "
                    f"(attempt {attempts})"
                )
                continue

            # Build initial routes for each cluster
            self.routes = []
            vehicle_name_map = {v.id: v.name for v in self.vrp_in.vehicles}

            for j in np.unique(self.route_points):
                route_filter = np.array(self.route_points) == j
                clients_in_route = [
                    self.vrp_in.clients[k] for k, active in enumerate(route_filter) if active
                ]
                if not clients_in_route:
                    continue

                points_tsp = [self.vrp_in.origin] + [c.address for c in clients_in_route]
                vehicle_id = self.vehicle_by_centroid.get(int(j))
                vehicle_name = vehicle_name_map.get(vehicle_id) if vehicle_id else None

                volume_liters = sum(c.volume_liters for c in clients_in_route)
                weight_kg = sum(c.weight_kg for c in clients_in_route)
                route_deliveries = len(clients_in_route)
                n_points = len(points_tsp)

                # Solve TSP for this route
                if self.is_southeast and n_points >= 2:
                    coords_list = [(p.latitude, p.longitude) for p in points_tsp]
                    route_line_coords, total_distance = osrm_service.get_route(coords_list)

                    if route_line_coords:
                        route_line = [
                            Coordinate(lat=c["lat"], lng=c["lng"]) for c in route_line_coords
                        ]
                        self.routes.append(
                            RouteDto(
                                distance_meters=total_distance,
                                route_line=route_line,
                                vehicle_id=vehicle_id,
                                vehicle_name=vehicle_name,
                                clients=clients_in_route,
                                volume_liters=volume_liters,
                                weight_kg=weight_kg,
                                route_deliveries=route_deliveries,
                            )
                        )
                        continue

                # Build distance matrix and solve TSP via OR-Tools
                dist_matrix = np.zeros((n_points, n_points), dtype=np.float64)
                for m in range(n_points):
                    for n in range(n_points):
                        if m != n:
                            dist_matrix[m, n] = haversine(
                                (points_tsp[m].latitude, points_tsp[m].longitude),
                                (points_tsp[n].latitude, points_tsp[n].longitude),
                                Unit.METERS,
                            )
                        else:
                            dist_matrix[m, n] = np.inf

                tour, total_distance = solve_tsp_ortools(dist_matrix)
                route_line = [
                    Coordinate(lat=points_tsp[idx].latitude, lng=points_tsp[idx].longitude)
                    for idx in tour
                ]

                self.routes.append(
                    RouteDto(
                        distance_meters=float(total_distance),
                        route_line=route_line,
                        vehicle_id=vehicle_id,
                        vehicle_name=vehicle_name,
                        clients=clients_in_route,
                        volume_liters=volume_liters,
                        weight_kg=weight_kg,
                        route_deliveries=route_deliveries,
                    )
                )

            # ALNS refinement
            dist_matrix_full = self._calculate_full_distance_matrix()
            logger.info(f"Running ALNS refinement (attempt {attempts + 1})...")
            alns = ALNSSolver(self.routes, self.vrp_in, dist_matrix_full, self.is_southeast)
            iterations = self.settings.ALNS_ITERATIONS if self.settings is not None else 1500
            improved_routes, unassigned_count, fleet_valid = alns.solve(
                iterations=iterations, initial_temp=100.0, cooling_rate=0.995, deadline=deadline  # type: ignore
            )

            if unassigned_count == 0 and fleet_valid:
                best_alns_routes = improved_routes
                break
            else:
                attempts += 1
                previous_active = len([r for r in self.routes if r.clients])
                route_offset = previous_active + 1
                self.number_of_centroides = int(np.ceil(self.number_of_centroides + 1))
                logger.info(
                    f"ALNS: unassigned={unassigned_count}, fleet_valid={fleet_valid}. "
                    f"Increasing routes to {self.number_of_centroides} (offset={route_offset})"
                )
                best_alns_routes = improved_routes

        if not best_alns_routes:
            raise ValueError("No feasible VRP solution found after multiple attempts.")

        return best_alns_routes


class VrpSolver:
    """Main VRP solver entry point.

    Always expands the informed fleet proportionally to the demand and solves
    with KMeans + ALNS (``VehicleRoutineProblemn``). Only very large instances
    fall back to the hierarchical ``LargeVehicleRoutineProblemn``.
    """

    def __init__(self, vrp_in: VrpIn, settings: Settings) -> None:
        self.vrp_in = vrp_in
        self.settings = settings

    def resolve(self) -> VrpOut:
        start_time = time.time()
        n_clients = len(self.vrp_in.clients)
        logger.info(f"VRP: {n_clients} clients, {len(self.vrp_in.vehicles)} vehicle types")

        if n_clients == 0:
            return VrpOut(
                id=self.vrp_in.id,
                origin=self.vrp_in.origin,
                routes=[],
                created_at=self.vrp_in.created_at,
                time_to_solve_ms=0.0,
            )

        # Expand the informed fleet to cover the demand (and/or target_routes).
        if self.vrp_in.vehicles:
            original_count = len(self.vrp_in.vehicles)
            self.vrp_in.vehicles = self._expand_fleet(self.vrp_in)
            logger.info(
                f"VRP: fleet expanded from {original_count} to "
                f"{len(self.vrp_in.vehicles)} vehicles"
            )

        # Deadline global de processamento por problema (SOLVER_TIME_BUDGET_SECONDS).
        budget = self.settings.SOLVER_TIME_BUDGET_SECONDS if self.settings is not None else 20
        deadline = time.monotonic() + budget
        logger.info(f"VRP: total time budget {budget}s per problem")

        if n_clients > 300:
            # Very large instance: hierarchical clustering + ALNS.
            from app.algorithms.vrp.large_vrp import LargeVehicleRoutineProblemn

            solver = LargeVehicleRoutineProblemn(self.vrp_in, settings=self.settings)
            return solver.resolve(deadline=deadline)

        # Primary path: KMeans + ALNS for every size.
        solver = VehicleRoutineProblemn(self.vrp_in, settings=self.settings)
        routes = solver.resolve(deadline=deadline)
        return VrpOut(
            id=self.vrp_in.id,
            origin=self.vrp_in.origin,
            routes=routes,
            created_at=self.vrp_in.created_at,
            time_to_solve_ms=(time.time() - start_time) * 1000,
        )

    def _expand_fleet(self, vrp_in: VrpIn) -> List[VehicleType]:
        """Replicate the informed fleet proportionally to the demand.

        The informed vehicle composition is repeated an integer number of times
        so that the total capacity covers the demand (volume, weight and
        delivery count) and/or the requested ``target_routes``. The ratio
        between vehicle types is preserved and each copy gets a fresh UUID. The
        result never exceeds ``n_clients`` vehicles.
        """
        vehicles = vrp_in.vehicles
        if not vehicles:
            return vehicles

        n_clients = len(vrp_in.clients)

        vols = [v.max_volume_liters for v in vehicles if v.max_volume_liters is not None]
        weights = [v.max_weight_kg for v in vehicles if v.max_weight_kg is not None]
        deliveries = [v.max_deliveries for v in vehicles if v.max_deliveries is not None]

        candidates: List[int] = [1]
        if vols:
            max_vol = max(vols)
            if max_vol > 0:
                candidates.append(int(np.ceil(sum(c.volume_liters for c in vrp_in.clients) / max_vol)))
        if weights:
            max_weight = max(weights)
            if max_weight > 0:
                candidates.append(int(np.ceil(sum(c.weight_kg for c in vrp_in.clients) / max_weight)))
        if deliveries:
            max_deliveries = max(deliveries)
            if max_deliveries > 0:
                candidates.append(int(np.ceil(n_clients / max_deliveries)))
        if vrp_in.target_routes is not None:
            candidates.append(int(vrp_in.target_routes))

        required = max(candidates)
        multiples = max(1, int(np.ceil(required / len(vehicles))))
        total = min(n_clients, len(vehicles) * multiples)

        expanded: List[VehicleType] = []
        for _ in range(multiples):
            for vehicle in vehicles:
                if len(expanded) >= total:
                    break
                # Preserve the informed UUID for the first occurrence, assign a
                # fresh one to every additional copy so all IDs stay unique.
                if len(expanded) < len(vehicles):
                    expanded.append(vehicle.model_copy())
                else:
                    expanded.append(vehicle.model_copy(update={"id": uuid.uuid4()}))
            if len(expanded) >= total:
                break

        return expanded
