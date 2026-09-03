---
name: fastapi-patterns
description: FastAPI patterns for async HTTP services and background workers — Pydantic DTOs with aliases, lifespan, concurrency limits, health checks, and exception handling. Use when working on FastAPI services in this repo.
---

# FastAPI Patterns

Patterns for the `opt-worker-solver` FastAPI service.

## DTOs (Pydantic v2)

- Prefer `BaseModel` with explicit types. Use `Optional[...]` for nullable fields.
- Enums as `class X(str, Enum)` so JSON serializes to plain strings.
- When the orchestrator sends camelCase (`matrixType`) but the model uses snake_case (`matrix_type`), add a field alias + `populate_by_name`:

```python
class MatrixRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    coordinates: List[Coordinate] = Field(min_length=2, max_length=500)
    matrix_type: MatrixType = Field(default=MatrixType.EUCLIDIAN, alias="matrixType")
```

- Validate payloads with `Model.model_validate(input_dict)` (never construct dicts by hand).
- Serialize with `model_dump(mode="json", by_alias=True)` when the consumer expects camelCase.

## Lifespan and startup

- Use `@asynccontextmanager` lifespan for connecting to external services (RabbitMQ, S3).
- Fail startup loudly when core deps are missing; log, don't crash, for optional ones.

## Concurrency limiting

- Use `asyncio.Semaphore` per job type, checked via `semaphore.locked()` before entering (return 503 / requeue if busy).
- Run CPU-bound solver work with `await asyncio.to_thread(...)` so the event loop stays responsive.

## HTTP endpoints

- `GET /health` always returns `{"status": "ok"}`.
- Sync fallback endpoints (e.g. `/logistic/tsp`) are fine for demos, but the async path via RabbitMQ is the primary flow.

## Error handling

- Wrap external calls (OSRM, S3) in try/except; log with `exc_info=True`; fail-open or raise a clear `RuntimeError`.
- Never leak stack traces to API responses.