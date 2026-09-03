---
name: pytest-patterns
description: Pytest patterns for the Python worker — async tests, mocking external services (S3, OSRM, RabbitMQ), fixtures, and running without live infrastructure. Use when writing or fixing worker tests.
---

# Pytest Patterns (solver-vgandolfi-dev-worker)

Tests live in `solver-vgandolfi-dev-worker/tests/`. Requirements: `requirements-dev.txt` (pytest, pytest-asyncio).

## Running

```bash
python -m pytest tests/ -v
```

## Async tests

- Use `pytest-asyncio`; mark tests with `@pytest.mark.asyncio` or set `asyncio_mode = auto` in `pyproject.toml`/`pytest.ini` (keep explicit marks if no config file exists).

## No live infrastructure

Tests must never require RabbitMQ/S3/OSRM:

- Instantiate `RabbitMQService(s3_service_mock)` directly (constructor only stores the ref).
- Mock heavy paths where they are resolved at runtime:
  - `app.algorithms.calculate_distances.calculate_distances`
  - `app.services.osrm_service.osrm_service` (and its `get_distance_matrix`)
- For `on_routing_message` end-to-end tests, fake `channel`/`exchange` with `AsyncMock` and assert the published `RoutingResultMessage` fields and `routing_key="routing.result"`.

## Fixtures (`tests/conftest.py`)

- `s3_service_mock` — `download_json` returns the input dict, `upload_json` returns a fake key.
- Input builders for the three job types (euclidian/street variants).

## What to cover

- DTO validation: min/max lengths, camelCase alias vs snake_case, defaults.
- Solver routing per job type (matrix euclidiano, matrix street, street failure → error).
- Total fields in the result message per type.
- Backwards compatibility: `matrix_type` snake_case still accepted after alias change.