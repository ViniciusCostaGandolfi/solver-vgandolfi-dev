import logging
import time
from typing import List

import numpy as np

from app.algorithms.calculate_distances import calculate_distances
from app.algorithms.tsp.lkh_heuristic import lin_kernighan_heuristic
from app.config import Settings
from app.dtos import Coordinate, MatrixType, TspRequest, TspResponse
from app.services.osrm_service import osrm_service

logger = logging.getLogger(__name__)


class TspLkhResolver:
    """TSP solver using Lin-Kernighan heuristic with optional OSRM distance matrix."""

    def __init__(self, request: TspRequest, settings: Settings) -> None:
        self.request = request
        self.settings = settings
        self.use_osrm = request.matrix_type == MatrixType.STREET

        # Origin is index 0, stops follow
        points_list: List[tuple[float, float]] = [
            (request.origin.latitude, request.origin.longitude)
        ]
        for stop in request.stops:
            points_list.append((stop.address.latitude, stop.address.longitude))

        self.points = np.array(points_list)
        self.points_list = points_list

    def _build_distance_matrix(self) -> np.ndarray:
        """Build distance matrix: OSRM if STREET, fallback to Haversine."""
        dist_matrix: np.ndarray | None = None

        if self.use_osrm:
            try:
                dist_matrix = osrm_service.get_distance_matrix(self.points_list)
                if dist_matrix is not None and dist_matrix.size == 0:
                    dist_matrix = None
            except Exception:
                logger.warning("OSRM Table unavailable, falling back to Haversine.")

        if dist_matrix is None:
            dist_matrix = calculate_distances(self.points)

        dist_matrix = dist_matrix.astype(np.float64)
        np.fill_diagonal(dist_matrix, np.inf)
        return dist_matrix

    def _build_route_line(self, ordered_points: List[tuple[float, float]]) -> List[Coordinate]:
        """Build route line geometry — OSRM if STREET, else direct coordinates."""
        if self.use_osrm:
            try:
                osrm_line, _ = osrm_service.get_route(ordered_points)
                if osrm_line:
                    return [Coordinate(lat=pt["lat"], lng=pt["lng"]) for pt in osrm_line]
            except Exception:
                logger.warning("OSRM Route unavailable for line, using direct coordinates.")

        return [Coordinate(lat=lat, lng=lng) for lat, lng in ordered_points]

    def resolve(self) -> TspResponse:
        start_time = time.time()

        # 1. Distance matrix
        dist_matrix = self._build_distance_matrix()

        # 2. LKH heuristic
        tour, tour_length = lin_kernighan_heuristic(
            dist_matrix, max_iterations=self.settings.LKH_MAX_ITERATIONS
        )

        # 3. Build ordered stops, removing return-to-start duplicate
        if len(tour) > 1 and tour[0] == tour[-1]:
            tour = tour[:-1]

        stops_ordered: List = []
        ordered_points: List[tuple[float, float]] = []

        for idx in tour:
            lat, lng = float(self.points[idx][0]), float(self.points[idx][1])
            ordered_points.append((lat, lng))
            if idx > 0:
                stops_ordered.append(self.request.stops[idx - 1])

        # Close the loop visually
        ordered_points.append((float(self.points[tour[0]][0]), float(self.points[tour[0]][1])))

        route_line = self._build_route_line(ordered_points)

        return TspResponse(
            optimized_stops=stops_ordered,
            route_line=route_line,
            distance_meters=float(tour_length),
            time_to_solve_ms=(time.time() - start_time) * 1000,
        )
