import logging
import time
from typing import Any, Dict, List, Optional

import numpy as np
from haversine import Unit, haversine
from sklearn.cluster import KMeans

from app.algorithms.vrp.alns_solver import ALNSSolver
from app.algorithms.vrp.capacited_kmeans import CapacitedKMeans
from app.algorithms.vrp.tsp_solver import solve_tsp_ortools
from app.config import Settings
from app.dtos import Address, Client, Coordinate, RouteDto, VehicleType, VrpIn, VrpOut
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


def _or_tools_status_is_infeasible(status: int, routing, routing_enums_pb2) -> bool:
    """Whether an OR-Tools search status means the CVRP instance is infeasible.

    Portable across OR-Tools 9.x versions: 9.10 exposes the ROUTING_* status
    constants on the RoutingModel class, while newer versions moved them to
    routing_enums_pb2.RoutingSearchStatus. Returns False for generic solver
    failures (e.g. ROUTING_FAIL).
    """
    names = (
        "ROUTING_INFEASIBLE",
        "ROUTING_INFEASIBLE_AFTER_TIME_LIMIT",
        "ROUTING_NO_SOLUTION_FOUND",
    )
    bad_statuses: set = set()
    for name in names:
        bad_statuses.add(getattr(type(routing), name, None))
        status_enum = getattr(routing_enums_pb2, "RoutingSearchStatus", None)
        if status_enum is not None:
            bad_statuses.add(getattr(status_enum, name, None))
    bad_statuses.discard(None)
    return status in bad_statuses


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

        if self.vrp_in.force_route_count is not None:
            return self.vrp_in.force_route_count

        vols = [v.max_volume_liters for v in self.vrp_in.vehicles if v.max_volume_liters is not None]
        weights = [v.max_weight_kg for v in self.vrp_in.vehicles if v.max_weight_kg is not None]
        deliveries = [v.max_deliveries for v in self.vrp_in.vehicles if v.max_deliveries is not None]

        max_vol = max(vols) if vols else None
        max_weight = max(weights) if weights else None
        max_deliveries = max(deliveries) if deliveries else None

        # Validate individual client demands don't exceed vehicle capacity
        for client in self.vrp_in.clients:
            if max_vol is not None and client.volume_liters > max_vol:
                raise ValueError(
                    f"Client {client.id} volume ({client.volume_liters}L) exceeds max vehicle capacity ({max_vol}L)"
                )
            if max_weight is not None and client.weight_kg > max_weight:
                raise ValueError(
                    f"Client {client.id} weight ({client.weight_kg}kg) exceeds max vehicle capacity ({max_weight}kg)"
                )

        cluster_candidates = []
        if max_vol is not None and self.volumes.sum() > 0:
            cluster_candidates.append(np.ceil(self.volumes.sum() / max_vol))
        if max_weight is not None and self.weights.sum() > 0:
            cluster_candidates.append(np.ceil(self.weights.sum() / max_weight))
        if max_deliveries is not None:
            cluster_candidates.append(np.ceil(n_clients / max_deliveries))

        min_clusters = max(cluster_candidates) if cluster_candidates else 1

        if min_clusters > n_clients:
            raise ValueError(
                f"VRP infeasible: capacity constraints require at least {int(min_clusters)} vehicles "
                f"but there are only {n_clients} clients."
            )

        n_clusters = min(int(min_clusters * 1.3), n_clients)
        return max(1, n_clusters)

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
            self.vrp_in.force_route_count,
            self.weights,
            route_offset,
        )
        status = capacited_kmeans.resolve()
        self.route_points = capacited_kmeans.route_points
        self.route_volumes = capacited_kmeans.route_volumes
        self.vehicle_by_centroid = capacited_kmeans.vehicle_by_centroid
        return status

    def resolve(self) -> List[RouteDto]:
        max_attempts = 10
        attempts = 0
        best_alns_routes: Optional[List[RouteDto]] = None
        route_offset = 0

        while attempts < max_attempts:
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
                iterations=iterations, initial_temp=100.0, cooling_rate=0.995  # type: ignore
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

    Dispatches to the appropriate solver based on instance size:
    - <= 50 clients: OR-Tools CVRP (exact solver)
    - > 50 clients: KMeans + ALNS (LargeVehicleRoutineProblemn)
    """

    def __init__(self, vrp_in: VrpIn, settings: Settings) -> None:
        self.vrp_in = vrp_in
        self.settings = settings

    def resolve(self) -> VrpOut:
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

        if n_clients <= 50:
            # Small instance: use OR-Tools CVRP directly
            return self._solve_ortools_cvrp()
        else:
            # Large instance: clustering + ALNS
            from app.algorithms.vrp.large_vrp import LargeVehicleRoutineProblemn

            solver = LargeVehicleRoutineProblemn(self.vrp_in, settings=self.settings)
            return solver.resolve()

    def _check_fleet_capacity(self, n_clients: int) -> None:
        """Raise InfeasibleVrpError when total demand exceeds fleet capacity.

        Sums client demand (volume, weight, delivery count) against the total
        fleet capacity for each dimension, mirroring the exact capacity
        defaults used by the OR-Tools model (a ``None`` field becomes
        effectively unlimited), so this only triggers on provably infeasible
        instances without depending on OR-Tools returning ``None``.
        """
        vehicles = self.vrp_in.vehicles

        total_volume = sum(int(c.volume_liters) for c in self.vrp_in.clients)
        total_weight = sum(int(c.weight_kg) for c in self.vrp_in.clients)
        total_deliveries = n_clients

        total_volume_capacity = sum(int(v.max_volume_liters or 1e9) for v in vehicles)
        total_weight_capacity = sum(int(v.max_weight_kg or 1e9) for v in vehicles)
        total_delivery_capacity = sum(int(v.max_deliveries or 1e6) for v in vehicles)

        if total_volume > total_volume_capacity:
            raise InfeasibleVrpError(
                f"Fleet capacity insufficient: total volume {total_volume}L > "
                f"{total_volume_capacity}L available"
            )
        if total_weight > total_weight_capacity:
            raise InfeasibleVrpError(
                f"Fleet capacity insufficient: total weight {total_weight}kg > "
                f"{total_weight_capacity}kg available"
            )
        if total_deliveries > total_delivery_capacity:
            raise InfeasibleVrpError(
                f"Fleet capacity insufficient: total deliveries {total_deliveries} > "
                f"{total_delivery_capacity} available"
            )

    def _solve_ortools_cvrp(self) -> VrpOut:
        """Solve small VRP instances directly with OR-Tools CVRP."""
        import numpy as np

        n_clients = len(self.vrp_in.clients)
        n_vehicles = min(len(self.vrp_in.vehicles), n_clients)
        start_time = time.time()

        # Fail fast on provably infeasible instances before invoking OR-Tools.
        self._check_fleet_capacity(n_clients)

        from ortools.constraint_solver import routing_enums_pb2, pywrapcp

        # Build point list: origin + all clients
        points = [(self.vrp_in.origin.latitude, self.vrp_in.origin.longitude)]
        for c in self.vrp_in.clients:
            points.append((c.address.latitude, c.address.longitude))

        n_nodes = n_clients + 1

        # Try OSRM for street distances, fallback to haversine
        if self.vrp_in.matrix_type.value == "STREET" and _check_southeast_region(self.vrp_in):
            dist_matrix = osrm_service.get_distance_matrix(points)
        else:
            dist_matrix = None

        if dist_matrix is None or dist_matrix.shape != (n_nodes, n_nodes):
            dist_matrix = np.zeros((n_nodes, n_nodes), dtype=np.float64)
            for i in range(n_nodes):
                for j in range(n_nodes):
                    if i != j:
                        dist_matrix[i, j] = haversine(points[i], points[j], Unit.METERS)

        # OR-Tools CVRP model
        manager = pywrapcp.RoutingIndexManager(n_nodes, n_vehicles, 0)
        routing = pywrapcp.RoutingModel(manager)

        def distance_callback(from_index: int, to_index: int) -> int:
            from_node = manager.IndexToNode(from_index)
            to_node = manager.IndexToNode(to_index)
            return int(dist_matrix[from_node][to_node])

        transit_callback_index = routing.RegisterTransitCallback(distance_callback)
        routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

        # Add capacity dimensions
        # Volume dimension
        if any(v.max_volume_liters is not None for v in self.vrp_in.vehicles):
            def volume_callback(from_index: int) -> int:
                node = manager.IndexToNode(from_index)
                if node == 0:
                    return 0
                return int(self.vrp_in.clients[node - 1].volume_liters)

            volume_callback_index = routing.RegisterUnaryTransitCallback(volume_callback)
            routing.AddDimensionWithVehicleCapacity(
                volume_callback_index,
                0,  # null capacity slack
                [int(v.max_volume_liters or 1e9) for v in self.vrp_in.vehicles],
                True,  # start cumul to zero
                "Volume",
            )

        # Weight dimension
        if any(v.max_weight_kg is not None for v in self.vrp_in.vehicles):
            def weight_callback(from_index: int) -> int:
                node = manager.IndexToNode(from_index)
                if node == 0:
                    return 0
                return int(self.vrp_in.clients[node - 1].weight_kg)

            weight_callback_index = routing.RegisterUnaryTransitCallback(weight_callback)
            routing.AddDimensionWithVehicleCapacity(
                weight_callback_index,
                0,
                [int(v.max_weight_kg or 1e9) for v in self.vrp_in.vehicles],
                True,
                "Weight",
            )

        # Delivery count dimension
        if any(v.max_deliveries is not None for v in self.vrp_in.vehicles):
            def delivery_callback(from_index: int) -> int:
                node = manager.IndexToNode(from_index)
                return 1 if node != 0 else 0

            delivery_callback_index = routing.RegisterUnaryTransitCallback(delivery_callback)
            routing.AddDimensionWithVehicleCapacity(
                delivery_callback_index,
                0,
                [int(v.max_deliveries or 1e6) for v in self.vrp_in.vehicles],
                True,
                "Deliveries",
            )

        # Solve
        search_parameters = pywrapcp.DefaultRoutingSearchParameters()
        search_parameters.first_solution_strategy = (
            routing_enums_pb2.FirstSolutionStrategy.PARALLEL_CHEAPEST_INSERTION
        )
        search_parameters.local_search_metaheuristic = (
            routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
        )
        search_parameters.time_limit.seconds = min(
            self.settings.VRP_TIMEOUT_SECONDS, 120
        )

        solution = routing.SolveWithParameters(search_parameters)

        if not solution:
            status = routing.status()
            logger.warning(
                f"OR-Tools CVRP returned no solution (status={status}) for "
                f"{n_clients} clients / {n_vehicles} vehicles"
            )
            if _or_tools_status_is_infeasible(status, routing, routing_enums_pb2):
                raise InfeasibleVrpError(
                    "No feasible solution: fleet cannot serve all clients within "
                    f"capacity limits (OR-Tools status={status})"
                )
            raise ValueError(
                f"OR-Tools CVRP failed to find a solution (status={status})."
            )

        # Extract routes
        routes: List[RouteDto] = []
        vehicle_name_map = {v.id: v.name for v in self.vrp_in.vehicles}

        for vehicle_idx in range(n_vehicles):
            index = routing.Start(vehicle_idx)
            if routing.IsEnd(index):
                continue

            route_clients: List[Client] = []
            route_points_coords = [(self.vrp_in.origin.latitude, self.vrp_in.origin.longitude)]
            route_distance = 0.0
            prev_index = index

            while not routing.IsEnd(index):
                node = manager.IndexToNode(index)
                if node != 0:
                    route_clients.append(self.vrp_in.clients[node - 1])
                    route_points_coords.append(
                        (self.vrp_in.clients[node - 1].address.latitude,
                         self.vrp_in.clients[node - 1].address.longitude)
                    )

                next_index = solution.Value(routing.NextVar(index))
                if not routing.IsEnd(next_index):
                    route_distance += dist_matrix[
                        manager.IndexToNode(index),
                        manager.IndexToNode(next_index),
                    ]
                index = next_index

            if not route_clients:
                continue

            # Close the loop back to origin
            route_points_coords.append((self.vrp_in.origin.latitude, self.vrp_in.origin.longitude))

            vehicle_id = self.vrp_in.vehicles[vehicle_idx].id
            volume = sum(c.volume_liters for c in route_clients)
            weight = sum(c.weight_kg for c in route_clients)

            # Get route geometry
            if self.vrp_in.matrix_type.value == "STREET":
                osrm_line, osrm_dist = osrm_service.get_route(route_points_coords)
                if osrm_line:
                    route_line = [Coordinate(lat=p["lat"], lng=p["lng"]) for p in osrm_line]
                    total_distance = osrm_dist
                else:
                    route_line = [
                        Coordinate(lat=lat, lng=lng) for lat, lng in route_points_coords
                    ]
                    total_distance = route_distance
            else:
                route_line = [
                    Coordinate(lat=lat, lng=lng) for lat, lng in route_points_coords
                ]
                total_distance = route_distance

            routes.append(
                RouteDto(
                    distance_meters=float(total_distance),
                    route_line=route_line,
                    vehicle_id=vehicle_id,
                    vehicle_name=vehicle_name_map.get(vehicle_id),
                    clients=route_clients,
                    volume_liters=volume,
                    weight_kg=weight,
                    route_deliveries=len(route_clients),
                )
            )

        return VrpOut(
            id=self.vrp_in.id,
            origin=self.vrp_in.origin,
            routes=routes,
            created_at=self.vrp_in.created_at,
            time_to_solve_ms=(time.time() - start_time) * 1000,
        )
