"""Tests for VRP infeasibility detection and INFEASIBLE result publishing.

- Pre-check in ``VrpSolver._check_fleet_capacity`` raises ``InfeasibleVrpError``
  with a clear message before OR-Tools is even invoked.
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
    return {
        "customer_name": f"Cliente {suffix}",
        "street_name": "Rua A",
        "street_number": str(suffix),
        "city": "Sao Paulo",
        "state": "SP",
        "postal_code": "01000-000",
        "latitude": -23.55 + suffix * 0.001,
        "longitude": -46.63 + suffix * 0.001,
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


# --- Pre-check: InfeasibleVrpError from fleet capacity ---------------------

def test_volume_exceeding_fleet_capacity_raises_infeasible():
    """130L of demand vs 120L of fleet volume capacity -> InfeasibleVrpError."""
    payload = _vrp_payload(
        volumes=[20, 20, 20, 20, 20, 20, 5, 5],  # total 130L
        weights=[1] * 8,
        vehicles=[
            {"name": "Van A", "max_volume_liters": 60},
            {"name": "Van B", "max_volume_liters": 60},
        ],  # total 120L
    )
    vrp_in = VrpIn.model_validate(payload)

    with pytest.raises(InfeasibleVrpError) as exc_info:
        VrpSolver(vrp_in, settings).resolve()

    msg = str(exc_info.value)
    assert "Fleet capacity insufficient" in msg
    assert "volume" in msg
    assert "130" in msg and "120" in msg


def test_weight_exceeding_fleet_capacity_raises_infeasible():
    """450kg of demand vs 400kg of fleet weight capacity -> weight message."""
    payload = _vrp_payload(
        volumes=[1, 1, 1],
        weights=[150, 150, 150],  # total 450kg
        vehicles=[
            {"name": "Truck A", "max_weight_kg": 200},
            {"name": "Truck B", "max_weight_kg": 200},
        ],  # total 400kg
    )
    vrp_in = VrpIn.model_validate(payload)

    with pytest.raises(InfeasibleVrpError) as exc_info:
        VrpSolver(vrp_in, settings).resolve()

    msg = str(exc_info.value)
    assert "Fleet capacity insufficient" in msg
    assert "weight" in msg
    assert "450" in msg and "400" in msg


def test_deliveries_exceeding_fleet_capacity_raises_infeasible():
    """8 clients vs 6 deliveries of fleet capacity -> deliveries message."""
    payload = _vrp_payload(
        volumes=[1] * 8,
        weights=[1] * 8,
        vehicles=[
            {"name": "Van A", "max_deliveries": 3},
            {"name": "Van B", "max_deliveries": 3},
        ],  # total 6 deliveries
    )
    vrp_in = VrpIn.model_validate(payload)

    with pytest.raises(InfeasibleVrpError) as exc_info:
        VrpSolver(vrp_in, settings).resolve()

    msg = str(exc_info.value)
    assert "Fleet capacity insufficient" in msg
    assert "deliveries" in msg
    assert "8" in msg and "6" in msg


# --- Feasible instance keeps solving ---------------------------------------

def test_feasible_instance_solves_with_ortools():
    """Feasible fleet keeps the OR-Tools flow and produces routes."""
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

    # OR-Tools with GUIDED_LOCAL_SEARCH keeps searching until the time limit
    # even on tiny instances, so shrink it for the test.
    fast_settings = settings.model_copy(update={"VRP_TIMEOUT_SECONDS": 1})

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
        volumes=[20] * 8,  # total 160L
        weights=[1] * 8,
        vehicles=[
            {"name": "Van A", "max_volume_liters": 60},
            {"name": "Van B", "max_volume_liters": 60},
        ],  # total 120L
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
    assert "160" in (published.errorMessage or "")
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