import logging
import time
from typing import List, Tuple

import numpy as np
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler

from app.dtos import Address, RouteDto, VrpIn, VrpOut
from app.algorithms.vrp.vrp_solver import VehicleRoutineProblemn
from app.config import Settings

logger = logging.getLogger(__name__)


class LargeVehicleRoutineProblemn:
    """VRP solver for large instances using hierarchical clustering.

    When the number of clients exceeds points_per_cluster, clients are grouped
    via KMeans clustering and each cluster is solved independently.
    """

    def __init__(self, vrp_in: VrpIn, points_per_cluster: int = 45, settings: Settings | None = None) -> None:
        self.vrp_in = vrp_in
        self.settings = settings
        self.routes: List[RouteDto] = []
        self.points = np.array(
            [[client.address.latitude, client.address.longitude] for client in vrp_in.clients]
        )
        self.points_per_cluster = points_per_cluster

    def _calculate_number_of_clusters(self) -> int:
        return max(1, int(np.ceil(len(self.points) / self.points_per_cluster)))

    def _calculate_clusters(self) -> Tuple[int, np.ndarray]:
        n_clusters = self._calculate_number_of_clusters()
        if len(self.points) <= n_clusters:
            return n_clusters, np.arange(len(self.points), dtype=np.int32)
        points_scaled = StandardScaler().fit_transform(self.points)
        kmeans = KMeans(n_clusters=n_clusters, n_init=20, random_state=42)
        kmeans.fit(points_scaled)
        return n_clusters, kmeans.labels_

    def resolve(self) -> VrpOut:
        if not self.vrp_in.vehicles:
            raise ValueError("At least one vehicle type is required.")

        time_to_solve = time.time()

        if len(self.points) > self.points_per_cluster:
            n_clusters, labels = self._calculate_clusters()
            logger.info(f"LargeVRP: {len(self.points)} clients split into {n_clusters} clusters")

            for cluster in range(n_clusters):
                mask = labels == cluster
                cluster_clients = [self.vrp_in.clients[i] for i, active in enumerate(mask) if active]
                if not cluster_clients:
                    continue

                cluster_vrp = VrpIn(
                    origin=self.vrp_in.origin,
                    vehicles=self.vrp_in.vehicles,
                    clients=cluster_clients,
                    matrix_type=self.vrp_in.matrix_type,
                    force_route_count=self.vrp_in.force_route_count,
                    max_route_distance=self.vrp_in.max_route_distance,
                )
                sub_solver = VehicleRoutineProblemn(cluster_vrp, settings=self.settings)
                cluster_routes = sub_solver.resolve()
                self.routes.extend(cluster_routes)
        else:
            logger.info(f"LargeVRP: {len(self.points)} clients — solving directly")
            sub_solver = VehicleRoutineProblemn(self.vrp_in, settings=self.settings)
            self.routes = sub_solver.resolve()

        return VrpOut(
            id=self.vrp_in.id,
            origin=self.vrp_in.origin,
            routes=self.routes,
            created_at=self.vrp_in.created_at,
            time_to_solve_ms=(time.time() - time_to_solve) * 1000,
        )
