import logging
from typing import Any, List, Tuple

import numpy as np
import requests

from app.config import settings

logger = logging.getLogger(__name__)


class OSRMService:
    """Service for interacting with the OSRM API (Table and Route services)."""

    def __init__(self) -> None:
        self.base_url = settings.OSRM_URL
        self.verify_ssl = settings.OSRM_VERIFY_SSL

    def get_distance_matrix(
        self, points: List[Tuple[float, float]]
    ) -> np.ndarray | None:
        """Compute a distance matrix in meters using the OSRM Table service.

        Args:
            points: List of (lat, lng) tuples.

        Returns:
            NxN float64 numpy array, or None on failure.
        """
        if not points:
            return np.array([], dtype=np.float64)

        coords = ";".join(f"{lng},{lat}" for lat, lng in points)
        url = f"{self.base_url}/table/v1/driving/{coords}?annotations=distance"

        try:
            response = requests.get(url, timeout=30, verify=self.verify_ssl)
            response.raise_for_status()
            data = response.json()

            if "distances" in data:
                matrix = np.array(data["distances"], dtype=np.float64)
                if np.any(np.isnan(matrix)) or np.any(matrix < 0):
                    logger.error("OSRM Table returned NaN or negative values — rejecting.")
                    return None
                return matrix

            logger.error(f"OSRM Table: 'distances' not in response: {data.get('code')}")
            return None

        except Exception as e:
            logger.error(f"OSRM Table error: {e}")
            return None

    def get_route(
        self, points: List[Tuple[float, float]]
    ) -> Tuple[List[dict[str, float]], float]:
        """Compute a full route polyline and distance using the OSRM Route service.

        Args:
            points: Sequence of (lat, lng) tuples (at least 2).

        Returns:
            (route_line, distance_meters). route_line is a list of {"lat": ..., "lng": ...}
            dicts. Returns ([], 0.0) on failure.
        """
        if len(points) < 2:
            return [], 0.0

        coords = ";".join(f"{lng},{lat}" for lat, lng in points)
        url = f"{self.base_url}/route/v1/driving/{coords}?overview=full&geometries=geojson"

        try:
            response = requests.get(url, timeout=30, verify=self.verify_ssl)
            response.raise_for_status()
            data = response.json()

            if data.get("code") == "Ok" and "routes" in data:
                route = data["routes"][0]
                distance = route["distance"]
                coordinates = route["geometry"]["coordinates"]
                route_line = [{"lat": lat, "lng": lng} for lng, lat in coordinates]
                return route_line, float(distance)

            logger.error(f"OSRM Route error: {data.get('code')}")
            return [], 0.0

        except Exception as e:
            logger.error(f"OSRM Route error: {e}")
            return [], 0.0

    def get_route_between(
        self, lat1: float, lng1: float, lat2: float, lng2: float
    ) -> List[dict]:
        """Compute the road route (polyline) between two points using the OSRM Route service.

        Args:
            lat1, lng1: Origin coordinate.
            lat2, lng2: Destination coordinate.

        Returns:
            List of {"lat": ..., "lng": ...} dicts describing the route polyline
            from point 1 to point 2. Returns [] on failure/error (never raises).
        """
        url = (
            f"{self.base_url}/route/v1/driving/"
            f"{lng1},{lat1};{lng2},{lat2}?overview=full&geometries=geojson"
        )

        try:
            response = requests.get(url, timeout=30, verify=self.verify_ssl)
            response.raise_for_status()
            data = response.json()

            if data.get("code") == "Ok" and "routes" in data:
                route = data["routes"][0]
                coordinates = route["geometry"]["coordinates"]
                return [{"lat": lat, "lng": lng} for lng, lat in coordinates]

            logger.error(f"OSRM Route (between) error: {data.get('code')}")
            return []

        except Exception as e:
            logger.error(f"OSRM Route (between) error: {e}")
            return []


osrm_service = OSRMService()
