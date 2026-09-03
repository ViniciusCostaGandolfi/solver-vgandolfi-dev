---
name: optimization-worker
description: How the optimization worker consumes jobs and produces results — RabbitMQ contract, S3 flow, OSRM integration, and solver routing. Use when modifying job processing, queues, or solver wiring in opt-worker-solver.
---

# Optimization Worker

Consumes async optimization jobs (TSP, VRP, DISTANCE_MATRIX) published by the orchestrator (Spring Boot).

## RabbitMQ contract

- Exchange: `routing.exchange` (direct, durable).
- Request queues (each with DLX args — MUST match the orchestrator exactly or RabbitMQ raises PRECONDITION_FAILED):
  - `routing.tsp.request.queue` ← `routing.tsp.request`
  - `routing.vrp.request.queue` ← `routing.vrp.request`
  - `routing.matrix.request.queue` ← `routing.matrix.request`
  - DLX args: `x-dead-letter-exchange=routing.exchange.dlq`, `x-dead-letter-routing-key=<queue>.dlq`
- Request message (camelCase): `{routingJobId, jobType, inputPath, userId, webhookUrl?}` — input payload JSON is stored in S3 at `inputPath`.
- Result published on `routing.result` as `RoutingResultMessage` (camelCase, see `app/dtos.py`).

## Flow (`RabbitMQService.on_routing_message`)

1. Validate `RoutingRequestMessage` from message body.
2. Download input JSON from S3 (`inputPath`).
3. Acquire semaphore for the job type (requeue/busy if locked).
4. `_solve(jobType, input_dict)` routes to the right solver.
5. Upload result JSON to S3 under `solutions/{jobId}/{uuid}.json`.
6. Build `RoutingResultMessage` with totals per type:
   - TSP → `totalDistanceMeters`, `totalStops`, `totalRoutes=1`, `solverType="LKH_TSP"`, `modelName="TspResponse"`
   - VRP → summed route totals, `solverType="LARGE_VRP"`, `modelName="VrpOut"`
   - DISTANCE_MATRIX → `totalStops=len(coordinates)`, `totalRoutes=1`, `solverType="OSRM_MATRIX"|"HAVERSINE_MATRIX"`, `modelName="DistanceMatrixResponse"`
7. On any exception publish a result with `solverStatus=ERROR` + `errorMessage` (never drop silently).

## Matrix computation

- STREET → `osrm_service.get_distance_matrix([(lat, lng), ...])` (OSRM table). None → raise.
- EUCLIDIAN → `calculate_distances(np.array([(lat, lng)...]))` (haversine meters).

## S3

- Bucket: `settings.S3_BUCKET_NAME` (aligned with orchestrator: `opt-vgandolfi-dev`).
- Path-style addressing required for MinIO.

## Testing rule

- Tests must run WITHOUT live RabbitMQ/S3/OSRM: instantiate `RabbitMQService(mock_s3)`, mock `osrm_service`/`calculate_distances`, use `AsyncMock` for channel/exchange.