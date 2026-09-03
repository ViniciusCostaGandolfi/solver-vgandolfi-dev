import numpy as np
from haversine import haversine, Unit


def calculate_distances(array: np.ndarray) -> np.ndarray:
    """Calculate pairwise Haversine distance matrix from a Nx2 array of (lat, lng)."""
    n = len(array)
    matrix = np.zeros((n, n), dtype=float)
    for i in range(n):
        for j in range(n):
            if i != j:
                matrix[i, j] = haversine(
                    (array[i][0], array[i][1]),
                    (array[j][0], array[j][1]),
                    unit=Unit.METERS,
                )
    return matrix
