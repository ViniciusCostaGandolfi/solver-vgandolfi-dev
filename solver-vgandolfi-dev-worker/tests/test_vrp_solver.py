"""Tests for the fleet-expansion and route-target changes in the VRP solver.

Covers:
- ``VrpSolver._expand_fleet`` replicates the informed composition proportionally
  to the demand, preserving the ratio and assigning fresh UUIDs to copies.
- ``target_routes`` drives ``_calculate_number_of_routes`` and is honoured by
  ``CapacitedKMeans``.
- ``CapacitedKMeans.max_center_for_point`` is ``min(10, n_centroides)``.
"""
import numpy as np

from app.algorithms.vrp.capacited_kmeans import CapacitedKMeans
from app.algorithms.vrp.vrp_solver import VrpSolver, VehicleRoutineProblemn
from app.config import settings
from app.dtos import VehicleType, VrpIn


def _address(suffix: int = 0) -> dict:
    # State outside the Southeast region keeps the solver on the haversine
    # fallback (no OSRM) for deterministic, fast tests.
    return {
        "customer_name": f"Cliente {suffix}",
        "street_name": "Rua A",
        "street_number": str(suffix),
        "city": "Salvador",
        "state": "BA",
        "postal_code": "40000-000",
        "latitude": -12.97 + suffix * 0.001,
        "longitude": -38.50 + suffix * 0.001,
    }


def _vrp_in(*, volumes, weights, vehicles, target_routes=None) -> VrpIn:
    return VrpIn.model_validate(
        {
            "origin": _address(-1),
            "clients": [
                {
                    "volume_liters": volumes[i],
                    "weight_kg": weights[i],
                    "address": _address(i),
                }
                for i in range(len(volumes))
            ],
            "vehicles": vehicles,
            "target_routes": target_routes,
        }
    )


# --- Fleet expansion --------------------------------------------------------

def test_expand_fleet_preserves_ratio_and_assigns_new_uuids():
    """[Van, Carro] with demand needing 3 routes -> 2 copies (ratio 1:1)."""
    vrp_in = _vrp_in(
        volumes=[50] * 6,  # total 300L
        weights=[1] * 6,
        vehicles=[
            {"name": "Van", "max_volume_liters": 100},
            {"name": "Carro", "max_volume_liters": 50},
        ],
    )
    original_ids = {str(v.id) for v in vrp_in.vehicles}

    expanded = VrpSolver(vrp_in, settings)._expand_fleet(vrp_in)

    # ceil(300/100) = 3 routes required; 2 informed -> 2 copies = 4 vehicles.
    assert len(expanded) == 4
    names = [v.name for v in expanded]
    assert names == ["Van", "Carro", "Van", "Carro"]
    assert names.count("Van") == names.count("Carro")

    # Every expanded vehicle has a unique id, and copies are new UUIDs.
    expanded_ids = [str(v.id) for v in expanded]
    assert len(set(expanded_ids)) == len(expanded_ids)
    assert any(i not in original_ids for i in expanded_ids)


def test_expand_fleet_uses_target_routes_and_never_exceeds_clients():
    """target_routes drives the expansion but is capped at n_clients."""
    vrp_in = _vrp_in(
        volumes=[1, 1, 1],  # capacity needs a single route
        weights=[1, 1, 1],
        vehicles=[{"name": "Van", "max_volume_liters": 1000}],
        target_routes=3,
    )

    expanded = VrpSolver(vrp_in, settings)._expand_fleet(vrp_in)

    assert len(expanded) == 3  # target of 3 routes -> 3 vehicles
    assert len({str(v.id) for v in expanded}) == 3


def test_expand_fleet_is_capped_at_number_of_clients():
    vrp_in = _vrp_in(
        volumes=[10] * 5,  # capacity needs ceil(50/10) = 5 routes
        weights=[1] * 5,
        vehicles=[
            {"name": "Van", "max_volume_liters": 10},
            {"name": "Carro", "max_volume_liters": 10},
        ],
    )

    expanded = VrpSolver(vrp_in, settings)._expand_fleet(vrp_in)

    assert len(expanded) == 5  # min(n_clients=5, 2 * multiples=6)


# --- target_routes ----------------------------------------------------------

def test_calculate_number_of_routes_honours_target_routes():
    """With target_routes set, n_centroides = max(capacity_base, target)."""
    vrp_in = _vrp_in(
        volumes=[10] * 10,  # all fit in one vehicle -> capacity_base = 1
        weights=[1] * 10,
        vehicles=[{"name": "Van", "max_volume_liters": 1000}],
        target_routes=5,
    )

    problem = VehicleRoutineProblemn(vrp_in, settings=settings)

    assert problem._calculate_number_of_routes() == 5


def test_capacited_kmeans_accepts_target_routes_and_uses_it():
    """The soft target penalty drives the solution to the requested route count."""
    n_points, n_centroides = 6, 3
    # Each client is far cheaper to serve from its designated centroid, which
    # forces the solution to actually spread across the target route count.
    distances = np.full((n_points, n_centroides), 1000.0)
    for i in range(n_points):
        distances[i, i % n_centroides] = 0.0

    ckm = CapacitedKMeans(
        points=np.zeros((n_points, 2)),
        points_volumes=np.zeros(n_points),
        distances=distances,
        n_routes=n_centroides,
        vehicles=[VehicleType(name="Van")],
        target_routes=n_centroides,
        points_weights=np.zeros(n_points),
    )

    status = ckm.resolve()

    assert status == 1
    assert len(set(ckm.route_points)) == n_centroides


# --- max_center_for_point ---------------------------------------------------

def test_max_center_for_point_is_min_10_and_n_centroides():
    def _ckm(n_centroides: int) -> CapacitedKMeans:
        return CapacitedKMeans(
            points=np.zeros((5, 2)),
            points_volumes=np.zeros(5),
            distances=np.zeros((5, n_centroides)),
            n_routes=n_centroides,
            vehicles=[VehicleType(name="Van")],
            target_routes=None,
            points_weights=np.zeros(5),
        )

    assert _ckm(3).max_center_for_point == 3
    assert _ckm(10).max_center_for_point == 10
    assert _ckm(15).max_center_for_point == 10
