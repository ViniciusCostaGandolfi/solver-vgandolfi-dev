import logging
from typing import Dict, List, Optional
import uuid

import numpy as np
from ortools.linear_solver import pywraplp

from app.dtos import VehicleType

logger = logging.getLogger(__name__)


class CapacitedKMeans:
    """Assign clients to routes (clusters) respecting capacity constraints using MILP.

    Uses OR-Tools SCIP solver to assign each client to a centroid/route, respecting
    vehicle capacity (volume, weight, deliveries) and optional route count constraints.
    """

    def __init__(
        self,
        points: np.ndarray,
        points_volumes: np.ndarray,
        distances: np.ndarray,
        n_routes: int,
        vehicles: List[VehicleType],
        force_route_count: Optional[int],
        points_weights: np.ndarray,
        route_offset: int = 0,
    ) -> None:
        self.points = points
        self.points_volumes = points_volumes
        self.points_weights = points_weights
        self.distances = distances
        self.n_routes = n_routes
        self.vehicles = vehicles
        self.force_route_count = force_route_count
        self.route_offset = route_offset
        self.route_points: List[int] = []
        self.route_volumes: List[float] = []
        self.route_weights: List[float] = []
        self.vehicle_by_centroid: Dict[int, uuid.UUID] = {}

    def resolve(self) -> int:
        """Solve assignment. Returns 1 on success, 0 if infeasible."""
        number_of_points = len(self.distances)
        number_of_centroides = len(self.distances[0])
        max_center_for_point = int(np.ceil(len(self.points) * 0.4))

        solver = pywraplp.Solver.CreateSolver("SCIP")
        if not solver:
            logger.error("SCIP solver not available, falling back.")
            return 0

        # Variables: x[i,j] = client i assigned to centroid j
        x: dict = {}
        for i in range(number_of_points):
            for j in range(number_of_centroides):
                x[i, j] = solver.BoolVar(f"x[{i},{j}]")

        # Variables: y[j,v] = vehicle type v assigned to centroid j
        y: dict = {}
        for j in range(number_of_centroides):
            for v_idx in range(len(self.vehicles)):
                y[j, v_idx] = solver.BoolVar(f"y[{j},{v_idx}]")

        # At most one vehicle type per centroid
        y_total: dict = {}
        for j in range(number_of_centroides):
            y_total[j] = sum(y[j, v_idx] for v_idx in range(len(self.vehicles)))
            solver.Add(y_total[j] <= 1)

        # Vehicle type usage limits
        for v_idx, v in enumerate(self.vehicles):
            if v.max_routes is not None:
                solver.Add(
                    sum(y[j, v_idx] for j in range(number_of_centroides)) <= v.max_routes
                )
            if v.min_routes > 0:
                solver.Add(
                    sum(y[j, v_idx] for j in range(number_of_centroides)) >= v.min_routes
                )

        # Total route count constraints
        if self.force_route_count is not None:
            solver.Add(sum(y_total[j] for j in range(number_of_centroides)) == self.force_route_count)
        else:
            solver.Add(sum(y_total[j] for j in range(number_of_centroides)) <= self.n_routes)
            if self.route_offset > 0:
                solver.Add(
                    sum(y_total[j] for j in range(number_of_centroides)) >= self.route_offset
                )

        # Objective: minimize distance + fixed vehicle costs
        objective_terms = []
        for i in range(number_of_points):
            for j in range(number_of_centroides):
                objective_terms.append(self.distances[i, j] * x[i, j])

        for j in range(number_of_centroides):
            for v_idx, v in enumerate(self.vehicles):
                cost = v.fixed_cost if v.fixed_cost > 0 else 1000
                objective_terms.append(cost * y[j, v_idx])

        # Constraints: each client assigned to exactly one centroid
        for i in range(number_of_points):
            # Limit nearest centroids for large instances
            distances_i = self.distances[i]
            if number_of_centroides > max_center_for_point:
                best_centroides = np.argsort(distances_i)[:max_center_for_point]
                for j in range(number_of_centroides):
                    if j not in best_centroides:
                        solver.Add(x[i, j] == 0)
            solver.Add(sum(x[i, j] for j in range(number_of_centroides)) == 1)

        # Capacity constraints per centroid
        for j in range(number_of_centroides):
            # Volume capacity
            vol_cap = solver.Sum(
                (v.max_volume_liters if v.max_volume_liters is not None else solver.Infinity())
                * y[j, v_idx]
                for v_idx, v in enumerate(self.vehicles)
            )
            solver.Add(
                solver.Sum(x[i, j] * self.points_volumes[i] for i in range(number_of_points))
                <= vol_cap
            )

            # Weight capacity
            wgt_cap = solver.Sum(
                (v.max_weight_kg if v.max_weight_kg is not None else solver.Infinity())
                * y[j, v_idx]
                for v_idx, v in enumerate(self.vehicles)
            )
            solver.Add(
                solver.Sum(x[i, j] * self.points_weights[i] for i in range(number_of_points))
                <= wgt_cap
            )

            # Delivery count capacity
            del_cap = solver.Sum(
                (v.max_deliveries if v.max_deliveries is not None else number_of_points)
                * y[j, v_idx]
                for v_idx, v in enumerate(self.vehicles)
            )
            solver.Add(
                solver.Sum(x[i, j] for i in range(number_of_points)) <= del_cap
            )

        solver.Minimize(solver.Sum(objective_terms))
        solver.SetTimeLimit(5000)

        params = pywraplp.MPSolverParameters()
        if number_of_points > 50:
            params.SetDoubleParam(params.RELATIVE_MIP_GAP, 0.01)

        status = solver.Solve(params)

        if status == pywraplp.Solver.INFEASIBLE:
            logger.info("CapacitedKMeans: INFEASIBLE")
            return 0

        # Extract assignment
        for i in range(number_of_points):
            for j in range(number_of_centroides):
                if x[i, j].solution_value() > 0.5:
                    self.route_points.append(j)

        for j in range(number_of_centroides):
            for v_idx, v in enumerate(self.vehicles):
                if y[j, v_idx].solution_value() > 0.5:
                    self.vehicle_by_centroid[j] = v.id
                    break

            if sum(x[i, j].solution_value() for i in range(number_of_points)) >= 1:
                vol = sum(
                    self.points_volumes[i] * x[i, j].solution_value()
                    for i in range(number_of_points)
                )
                self.route_volumes.append(vol)

        return 1
