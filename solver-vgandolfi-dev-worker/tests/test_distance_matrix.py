"""Tests for async DISTANCE_MATRIX support in the worker.

`_solve` is exercised directly (no RabbitMQ) by instantiating
`RabbitMQService(s3_service_mock)` and mocking the heavy workers:
- `app.algorithms.calculate_distances.calculate_distances` (EUCLIDIAN path)
- `app.services.osrm_service.osrm_service.get_distance_matrix` (STREET path)
"""
import asyncio
import uuid
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch

import numpy as np
import pytest
from pydantic import ValidationError

from app.dtos import (
    DistanceMatrixResponse,
    JobType,
    MatrixRequest,
    MatrixType,
    RoutingRequestMessage,
    RoutingResultMessage,
    VrpSolutionStatus,
)
from app.services.rabbitmq_service import RabbitMQService


class _AsyncContextManager:
    """Minimal async context manager used to fake `message.process()`."""

    async def __aenter__(self):
        return None

    async def __aexit__(self, *exc):
        return False


@pytest.mark.asyncio
async def test_euclidian_matrix_uses_haversine(s3_service_mock, euclidian_input):
    """EUCLIDIAN path calls calculate_distances and returns a response matrix."""
    fixed_matrix = np.array([[0.0, 10.0, 20.0], [10.0, 0.0, 30.0], [20.0, 30.0, 0.0]])
    svc = RabbitMQService(s3_service_mock)

    with patch(
        "app.algorithms.calculate_distances.calculate_distances",
        return_value=fixed_matrix,
    ) as mock_calc:
        response = await svc._solve(JobType.DISTANCE_MATRIX, euclidian_input)

    assert isinstance(response, DistanceMatrixResponse)
    assert response.matrix == fixed_matrix.tolist()
    assert len(response.coordinates) == 3
    assert response.time_to_solve_ms >= 0

    mock_calc.assert_called_once()
    points_arg = mock_calc.call_args.args[0]
    assert points_arg.shape == (3, 2)
    assert points_arg.dtype == float


@pytest.mark.asyncio
async def test_street_matrix_uses_osrm(s3_service_mock, street_input):
    """STREET path calls osrm_service.get_distance_matrix with (lat, lng) tuples."""
    osrm_matrix = np.array(
        [[0.0, 500.0, 900.0], [500.0, 0.0, 400.0], [900.0, 400.0, 0.0]]
    )
    svc = RabbitMQService(s3_service_mock)

    with patch(
        "app.services.osrm_service.osrm_service.get_distance_matrix",
        return_value=osrm_matrix,
    ) as mock_osrm:
        response = await svc._solve(JobType.DISTANCE_MATRIX, street_input)

    assert isinstance(response, DistanceMatrixResponse)
    assert response.matrix == osrm_matrix.tolist()
    assert response.time_to_solve_ms >= 0

    mock_osrm.assert_called_once()
    points_arg = mock_osrm.call_args.args[0]
    assert points_arg == [(-23.5505, -46.6333), (-23.5614, -46.6559), (-23.5489, -46.6388)]


@pytest.mark.asyncio
async def test_street_matrix_osrm_none_raises(s3_service_mock, street_input):
    """OSRM returning None (failure) must raise instead of returning garbage."""
    svc = RabbitMQService(s3_service_mock)

    with patch(
        "app.services.osrm_service.osrm_service.get_distance_matrix",
        return_value=None,
    ):
        with pytest.raises(RuntimeError, match="OSRM distance matrix request failed"):
            await svc._solve(JobType.DISTANCE_MATRIX, street_input)


def test_matrix_request_min_two_coordinates():
    """A single coordinate must be rejected by validation."""
    with pytest.raises(ValidationError):
        MatrixRequest.model_validate(
            {
                "matrixType": "EUCLIDIAN",
                "coordinates": [{"lat": -23.5505, "lng": -46.6333}],
            }
        )


def test_matrix_request_max_five_hundred_coordinates():
    """More than 500 coordinates must be rejected by validation."""
    many = [{"lat": -23.0, "lng": -46.0} for _ in range(501)]
    with pytest.raises(ValidationError):
        MatrixRequest.model_validate({"matrixType": "EUCLIDIAN", "coordinates": many})


def test_matrix_request_accepts_camel_case_matrix_type():
    """Orchestrator payload uses `matrixType` (camelCase) and it must map correctly."""
    req = MatrixRequest.model_validate(
        {
            "matrixType": "STREET",
            "coordinates": [
                {"lat": -23.5505, "lng": -46.6333},
                {"lat": -23.5614, "lng": -46.6559},
            ],
        }
    )
    assert req.matrix_type == MatrixType.STREET


def test_matrix_request_accepts_snake_case_matrix_type():
    """Snake_case `matrix_type` also works (populate_by_name)."""
    req = MatrixRequest.model_validate(
        {
            "matrix_type": "EUCLIDIAN",
            "coordinates": [
                {"lat": -23.5505, "lng": -46.6333},
                {"lat": -23.5614, "lng": -46.6559},
            ],
        }
    )
    assert req.matrix_type == MatrixType.EUCLIDIAN


def test_matrix_request_defaults_to_euclidian():
    """When matrixType is omitted, EUCLIDIAN is the default."""
    req = MatrixRequest.model_validate(
        {
            "coordinates": [
                {"lat": -23.5505, "lng": -46.6333},
                {"lat": -23.5614, "lng": -46.6559},
            ]
        }
    )
    assert req.matrix_type == MatrixType.EUCLIDIAN


def _matrix_consumer(s3_service_mock):
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


def _matrix_request_message(body_payload: dict) -> MagicMock:
    message = MagicMock()
    message.body = RoutingRequestMessage(
        routingJobId=uuid.uuid4(),
        jobType=JobType.DISTANCE_MATRIX,
        inputPath="inputs/matrix.json",
        userId=uuid.uuid4(),
    ).model_dump_json().encode()
    message.correlation_id = "corr-matrix-1"
    message.process.return_value = _AsyncContextManager()
    return message


@pytest.mark.asyncio
async def test_on_routing_message_euclidian_metadata(s3_service_mock, euclidian_input):
    """Full consumer path for EUCLIDIAN: HAVERSINE_MATRIX metadata and totals."""
    svc, exchange_mock = _matrix_consumer(s3_service_mock)
    s3_service_mock.download_json.return_value = euclidian_input
    s3_service_mock.upload_json.return_value = "solutions/matrix-euclidian.json"

    fixed_matrix = np.array([[0.0, 1.0, 2.0], [1.0, 0.0, 3.0], [2.0, 3.0, 0.0]])
    with patch(
        "app.algorithms.calculate_distances.calculate_distances",
        return_value=fixed_matrix,
    ):
        await svc.on_routing_message(_matrix_request_message(euclidian_input))

    publish_kwargs = exchange_mock.publish.call_args
    assert publish_kwargs.kwargs["routing_key"] == "routing.result"
    published = RoutingResultMessage.model_validate_json(publish_kwargs.args[0].body)
    assert published.jobType == JobType.DISTANCE_MATRIX
    assert published.solverType == "HAVERSINE_MATRIX"
    assert published.modelName == "DistanceMatrixResponse"
    assert published.solverStatus == VrpSolutionStatus.OPTIMAL
    assert published.totalStops == 3
    assert published.totalRoutes == 1
    assert published.totalDistanceMeters is None
    assert published.outputPath == "solutions/matrix-euclidian.json"
    assert published.errorMessage is None


@pytest.mark.asyncio
async def test_on_routing_message_street_metadata(s3_service_mock, street_input):
    """Full consumer path for STREET: OSRM_MATRIX metadata and totals."""
    svc, exchange_mock = _matrix_consumer(s3_service_mock)
    s3_service_mock.download_json.return_value = street_input
    s3_service_mock.upload_json.return_value = "solutions/matrix-street.json"

    osrm_matrix = np.zeros((3, 3), dtype=float)
    with patch(
        "app.services.osrm_service.osrm_service.get_distance_matrix",
        return_value=osrm_matrix,
    ):
        await svc.on_routing_message(_matrix_request_message(street_input))

    publish_kwargs = exchange_mock.publish.call_args
    published = RoutingResultMessage.model_validate_json(publish_kwargs.args[0].body)
    assert published.solverType == "OSRM_MATRIX"
    assert published.modelName == "DistanceMatrixResponse"
    assert published.totalStops == 3
    assert published.totalRoutes == 1
    assert published.outputPath == "solutions/matrix-street.json"