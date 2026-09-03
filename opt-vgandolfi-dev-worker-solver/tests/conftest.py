"""Shared fixtures for the opt-worker-solver test suite."""
from unittest.mock import MagicMock

import pytest


@pytest.fixture
def s3_service_mock():
    """Stub S3Service — not used by `_solve`, but required by RabbitMQService."""
    return MagicMock()


@pytest.fixture
def coordinates():
    """Three coordinates as the orchestrator sends them (camelCase payload)."""
    return [
        {"lat": -23.5505, "lng": -46.6333},
        {"lat": -23.5614, "lng": -46.6559},
        {"lat": -23.5489, "lng": -46.6388},
    ]


@pytest.fixture
def euclidian_input(coordinates):
    """DISTANCE_MATRIX input payload with EUCLIDIAN matrix type."""
    return {"matrixType": "EUCLIDIAN", "coordinates": coordinates}


@pytest.fixture
def street_input(coordinates):
    """DISTANCE_MATRIX input payload with STREET matrix type."""
    return {"matrixType": "STREET", "coordinates": coordinates}