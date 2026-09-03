import numpy as np
from ortools.constraint_solver import routing_enums_pb2
from ortools.constraint_solver import pywrapcp


def solve_tsp_ortools(
    dist_matrix: np.ndarray, timeout_ms: int = 1000
) -> tuple[np.ndarray, float]:
    """Solve TSP for a single route using OR-Tools.

    Args:
        dist_matrix: NxN distance matrix. Index 0 is the depot/origin.
        timeout_ms: Solver time limit in milliseconds.

    Returns:
        (tour, tour_length) where tour is an array of node indices including
        the return to depot at the end.
    """
    num_nodes = len(dist_matrix)
    if num_nodes <= 2:
        tour = list(range(num_nodes)) + [0]
        return np.array(tour, dtype=np.int32), sum(
            dist_matrix[tour[i], tour[i + 1]] for i in range(len(tour) - 1)
        )

    manager = pywrapcp.RoutingIndexManager(num_nodes, 1, 0)
    routing = pywrapcp.RoutingModel(manager)

    def distance_callback(from_index: int, to_index: int) -> int:
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return int(dist_matrix[from_node][to_node])

    transit_callback_index = routing.RegisterTransitCallback(distance_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = (
        routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    )
    search_parameters.local_search_metaheuristic = (
        routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    )
    search_parameters.time_limit.seconds = timeout_ms // 1000

    solution = routing.SolveWithParameters(search_parameters)

    if solution:
        tour = []
        index = routing.Start(0)
        while not routing.IsEnd(index):
            tour.append(manager.IndexToNode(index))
            index = solution.Value(routing.NextVar(index))
        tour.append(manager.IndexToNode(index))
        return np.array(tour, dtype=np.int32), float(solution.ObjectiveValue())

    # Fallback: identity tour
    fallback = np.arange(num_nodes + 1, dtype=np.int32) % num_nodes
    return fallback, 0.0
