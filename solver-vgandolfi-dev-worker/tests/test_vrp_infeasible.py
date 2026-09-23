"""Tests for VRP infeasibility detection and INFEASIBLE result publishing.

- A single client whose demand cannot fit in the largest vehicle raises
  ``InfeasibleVrpError`` with a clear message.
- The total fleet capacity is no longer a limit: the fleet is expanded
  proportionally to the demand, so total demand above the informed fleet size
  is now feasible.
- ``on_routing_message`` publishes ``solverStatus=INFEASIBLE`` for these domain
  errors, while generic errors keep the existing ERROR status.
"""
import asyncio
import uuid
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.algorithms.vrp.vrp_solver import VrpSolver
from app.config import settings
from app.dtos import (
    JobType,
    RoutingRequestMessage,
    RoutingResultMessage,
    VrpIn,
    VrpOut,
    VrpSolutionStatus,
)
from app.exceptions import InfeasibleVrpError
from app.services.rabbitmq_service import RabbitMQService


def _address(suffix: int = 0) -> dict:
    # State outside the Southeast region so the solver skips OSRM and uses the
    # deterministic haversine fallback in tests.
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


def _vrp_payload(*, volumes: list, weights: list, vehicles: list) -> dict:
    n_clients = len(volumes)
    clients = [
        {
            "volume_liters": volumes[i],
            "weight_kg": weights[i],
            "address": _address(i),
        }
        for i in range(n_clients)
    ]
    return {
        "origin": _address(-1),
        "clients": clients,
        "vehicles": vehicles,
    }


# --- Per-client infeasibility ----------------------------------------------

def test_client_volume_exceeding_largest_vehicle_raises_infeasible():
    """A single client above every vehicle's volume -> InfeasibleVrpError."""
    payload = _vrp_payload(
        volumes=[80, 10, 10],  # 80L client cannot fit in a 60L van
        weights=[1, 1, 1],
        vehicles=[{"name": "Van A", "max_volume_liters": 60}],
    )
    vrp_in = VrpIn.model_validate(payload)

    with pytest.raises(InfeasibleVrpError) as exc_info:
        VrpSolver(vrp_in, settings).resolve()

    msg = str(exc_info.value)
    assert "volume" in msg
    assert "80" in msg and "60" in msg


def test_client_weight_exceeding_largest_vehicle_raises_infeasible():
    """A single client above every vehicle's weight -> InfeasibleVrpError."""
    payload = _vrp_payload(
        volumes=[1, 1, 1],
        weights=[250, 10, 10],  # 250kg client cannot fit in a 200kg truck
        vehicles=[{"name": "Truck A", "max_weight_kg": 200}],
    )
    vrp_in = VrpIn.model_validate(payload)

    with pytest.raises(InfeasibleVrpError) as exc_info:
        VrpSolver(vrp_in, settings).resolve()

    msg = str(exc_info.value)
    assert "weight" in msg
    assert "250" in msg and "200" in msg


def test_client_deliveries_exceeding_largest_vehicle_raises_infeasible():
    """A vehicle that cannot carry a single delivery -> InfeasibleVrpError."""
    payload = _vrp_payload(
        volumes=[1, 1],
        weights=[1, 1],
        vehicles=[{"name": "Van A", "max_deliveries": 0}],
    )
    vrp_in = VrpIn.model_validate(payload)

    with pytest.raises(InfeasibleVrpError) as exc_info:
        VrpSolver(vrp_in, settings).resolve()

    msg = str(exc_info.value)
    assert "deliver" in msg


# --- Total demand above the informed fleet is now feasible -----------------

def test_total_demand_above_fleet_capacity_is_expanded_and_feasible():
    """Total demand above the informed fleet no longer fails — fleet expands."""
    payload = _vrp_payload(
        volumes=[20] * 8,  # total 160L vs 60L informed
        weights=[1] * 8,
        vehicles=[{"name": "Van A", "max_volume_liters": 60}],
    )
    vrp_in = VrpIn.model_validate(payload)

    fast_settings = settings.model_copy(
        update={"ALNS_ITERATIONS": 20, "SOLVER_TIME_BUDGET_SECONDS": 10}
    )
    result = VrpSolver(vrp_in, fast_settings).resolve()

    assert isinstance(result, VrpOut)
    assert sum(len(r.clients) for r in result.routes) == 8


# --- Feasible instance keeps solving ---------------------------------------

def test_feasible_instance_solves():
    """Feasible fleet solves through the KMeans + ALNS path and yields routes."""
    payload = _vrp_payload(
        volumes=[10, 15],
        weights=[5, 5],
        vehicles=[
            {
                "name": "Van",
                "max_volume_liters": 100,
                "max_weight_kg": 100,
                "max_deliveries": 10,
            }
        ],
    )
    vrp_in = VrpIn.model_validate(payload)

    fast_settings = settings.model_copy(
        update={"ALNS_ITERATIONS": 20, "SOLVER_TIME_BUDGET_SECONDS": 10}
    )
    result = VrpSolver(vrp_in, fast_settings).resolve()

    assert isinstance(result, VrpOut)
    assert len(result.routes) == 1
    assert sum(len(r.clients) for r in result.routes) == 2


# --- Publish: INFEASIBLE vs ERROR ------------------------------------------

class _AsyncContextManager:
    async def __aenter__(self):
        return None

    async def __aexit__(self, *exc):
        return False


def _vrp_consumer(s3_service_mock):
    """RabbitMQService wired with fake channel/exchange for on_routing_message."""
    svc = RabbitMQService(s3_service_mock)
    svc.concurrency_limiter = SimpleNamespace(
        tsp_semaphore=asyncio.Semaphore(1),
        vrp_semaphore=asyncio.Semaphore(1),
        matrix_semaphore=asyncio.Semaphore(1),
    )
    exchange_mock = MagicMock()
    exchange_mock.publish = AsyncMock()
    svc.channel = MagicMock()
    svc.channel.get_exchange = AsyncMock(return_value=exchange_mock)
    return svc, exchange_mock


def _vrp_request_message() -> MagicMock:
    message = MagicMock()
    message.body = RoutingRequestMessage(
        routingJobId=uuid.uuid4(),
        jobType=JobType.VRP,
        inputPath="inputs/vrp.json",
        userId=uuid.uuid4(),
    ).model_dump_json().encode()
    message.correlation_id = "corr-vrp-1"
    message.process.return_value = _AsyncContextManager()
    return message


@pytest.mark.asyncio
async def test_on_routing_message_publishes_infeasible(s3_service_mock):
    """Infeasible VRP payload -> solverStatus=INFEASIBLE with clear message."""
    svc, exchange_mock = _vrp_consumer(s3_service_mock)
    s3_service_mock.download_json.return_value = _vrp_payload(
        volumes=[80, 10],  # 80L client cannot fit in a 60L van
        weights=[1, 1],
        vehicles=[{"name": "Van A", "max_volume_liters": 60}],
    )

    await svc.on_routing_message(_vrp_request_message())

    publish_kwargs = exchange_mock.publish.call_args
    assert publish_kwargs.kwargs["routing_key"] == "routing.result"
    published = RoutingResultMessage.model_validate_json(publish_kwargs.args[0].body)
    assert published.jobType == JobType.VRP
    assert published.solverStatus == VrpSolutionStatus.INFEASIBLE
    assert published.solverType == "VRP"
    assert published.modelName == "VrpOut"
    assert "volume" in (published.errorMessage or "")
    assert "80" in (published.errorMessage or "")
    assert published.outputPath is None
    s3_service_mock.upload_json.assert_not_called()


@pytest.mark.asyncio
async def test_on_routing_message_generic_error_still_error(s3_service_mock):
    """Non-infeasible failures keep the existing ERROR status."""
    svc, exchange_mock = _vrp_consumer(s3_service_mock)
    s3_service_mock.download_json.return_value = _vrp_payload(
        volumes=[10, 15],
        weights=[5, 5],
        vehicles=[{"name": "Van", "max_volume_liters": 100}],
    )

    async def _boom(*args, **kwargs):
        raise ValueError("some random solver failure")

    svc._solve = _boom

    await svc.on_routing_message(_vrp_request_message())

    published = RoutingResultMessage.model_validate_json(
        exchange_mock.publish.call_args.args[0].body
    )
    assert published.solverStatus == VrpSolutionStatus.ERROR
    assert "some random solver failure" in (published.errorMessage or "")
    assert published.solverType == "ERROR"
    assert published.modelName == "ERROR"