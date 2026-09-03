import numpy as np
from numba import jit
from typing import Tuple


@jit(nopython=True, cache=True)
def calculate_tour_length(tour: np.ndarray, dist_matrix: np.ndarray) -> float:
    length = 0.0
    for i in range(len(tour) - 1):
        length += dist_matrix[tour[i], tour[i + 1]]
    length += dist_matrix[tour[-1], tour[0]]
    return length


@jit(nopython=True, cache=True)
def find_best_2opt_move(tour: np.ndarray, dist_matrix: np.ndarray) -> Tuple[int, int, float]:
    n = len(tour)
    best_delta = 0.0
    best_i, best_j = -1, -1

    for i in range(n - 2):
        for j in range(i + 2, n):
            # Skip reversal of the entire tour (same route reversed)
            if i == 0 and j == n - 1:
                continue

            t_i = tour[i]
            t_i_plus_1 = tour[i + 1]
            t_j = tour[j]
            t_j_plus_1 = tour[(j + 1) % n]

            delta = (
                dist_matrix[t_i, t_j]
                + dist_matrix[t_i_plus_1, t_j_plus_1]
                - dist_matrix[t_i, t_i_plus_1]
                - dist_matrix[t_j, t_j_plus_1]
            )

            if delta < best_delta:
                best_delta = delta
                best_i = i
                best_j = j

    return best_i, best_j, best_delta


@jit(nopython=True, cache=True)
def apply_2opt_move(tour: np.ndarray, i: int, j: int) -> np.ndarray:
    new_tour = tour.copy()
    new_tour[i + 1 : j + 1] = new_tour[i + 1 : j + 1][::-1]
    return new_tour


def lin_kernighan_heuristic(
    dist_matrix: np.ndarray, max_iterations: int = 30000
) -> Tuple[np.ndarray, float]:
    """
    Lin-Kernighan heuristic (2-opt improvement) for the TSP.

    Args:
        dist_matrix: NxN distance matrix (float64, diagonal should be inf).
        max_iterations: Maximum improvement iterations.

    Returns:
        (tour, tour_length) where tour includes the return-to-origin edge.
    """
    n = dist_matrix.shape[0]
    tour = np.arange(0, n, dtype=np.int32)
    improved = True
    iteration = 0

    while improved and iteration < max_iterations:
        improved = False
        best_i, best_j, best_delta = find_best_2opt_move(tour, dist_matrix)

        # Apply only if improvement is strictly negative
        if best_delta < -1e-6:
            tour = apply_2opt_move(tour, best_i, best_j)
            improved = True

        iteration += 1

    tour_length = calculate_tour_length(tour, dist_matrix)
    tour = np.append(tour, tour[0])  # Close the cycle
    return tour, tour_length
