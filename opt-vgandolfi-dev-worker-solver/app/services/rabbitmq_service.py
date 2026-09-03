import asyncio
import json
import logging
import time
from typing import Optional

import aio_pika

from app.config import settings
from app.dtos import (
    DistanceMatrixResponse,
    JobType,
    MatrixRequest,
    MatrixType,
    RoutingRequestMessage,
    RoutingResultMessage,
    VrpIn,
    VrpSolutionStatus,
)
from app.exceptions import InfeasibleVrpError

logger = logging.getLogger(__name__)


class RabbitMQService:
    def __init__(self, s3_service):
        self.s3_service = s3_service
        self.connection: Optional[aio_pika.Connection] = None
        self.channel: Optional[aio_pika.Channel] = None
        self.concurrency_limiter = None

    def set_concurrency_limiter(self, limiter):
        self.concurrency_limiter = limiter

    async def connect(self):
        url = f"amqp://{settings.MQ_USER}:{settings.MQ_PASSWORD}@{settings.MQ_HOST}:{settings.MQ_PORT}/"
        self.connection = await aio_pika.connect_robust(url)
        self.channel = await self.connection.channel()
        await self.channel.set_qos(prefetch_count=1)

        exchange = await self.channel.declare_exchange(
            "routing.exchange", aio_pika.ExchangeType.DIRECT, durable=True
        )

        # TSP async queue
        tsp_queue = await self.channel.declare_queue(
            "routing.tsp.request.queue",
            durable=True,
            arguments={
                "x-dead-letter-exchange": "routing.exchange.dlq",
                "x-dead-letter-routing-key": "routing.tsp.request.queue.dlq",
            },
        )
        await tsp_queue.bind(exchange, routing_key="routing.tsp.request")
        await tsp_queue.consume(self.on_routing_message)

        # VRP async queue
        vrp_queue = await self.channel.declare_queue(
            "routing.vrp.request.queue",
            durable=True,
            arguments={
                "x-dead-letter-exchange": "routing.exchange.dlq",
                "x-dead-letter-routing-key": "routing.vrp.request.queue.dlq",
            },
        )
        await vrp_queue.bind(exchange, routing_key="routing.vrp.request")
        await vrp_queue.consume(self.on_routing_message)

        # DISTANCE_MATRIX async queue
        matrix_queue = await self.channel.declare_queue(
            "routing.matrix.request.queue",
            durable=True,
            arguments={
                "x-dead-letter-exchange": "routing.exchange.dlq",
                "x-dead-letter-routing-key": "routing.matrix.request.queue.dlq",
            },
        )
        await matrix_queue.bind(exchange, routing_key="routing.matrix.request")
        await matrix_queue.consume(self.on_routing_message)

        logger.info(
            "RabbitMQ consumers started (TSP async + VRP + DISTANCE_MATRIX). prefetch_count=1."
        )

    async def on_routing_message(self, message: aio_pika.IncomingMessage):
        async with message.process(requeue=False):
            start_time = time.time()
            req_msg: Optional[RoutingRequestMessage] = None
            result_msg: Optional[RoutingResultMessage] = None

            try:
                req_msg = RoutingRequestMessage.model_validate_json(message.body.decode())
                logger.info(f"Received {req_msg.jobType} job={req_msg.routingJobId}")

                input_dict = self.s3_service.download_json(req_msg.inputPath)
                semaphore = self._get_semaphore(req_msg.jobType)

                if semaphore.locked():
                    logger.warning(f"{req_msg.jobType} semaphore locked, requeueing")
                    raise asyncio.TimeoutError("Resource busy")

                async with semaphore:
                    response = await self._solve(req_msg.jobType, input_dict)
                    elapsed_ms = int((time.time() - start_time) * 1000)

                    output_path = self.s3_service.upload_json(
                        response.model_dump(mode="json", by_alias=True),
                        prefix=f"solutions/{req_msg.routingJobId}",
                    )

                    total_distance_meters = None
                    total_stops = None
                    total_routes = None
                    solver_type = "LARGE_VRP"
                    model_name = "VrpOut"
                    if req_msg.jobType == JobType.TSP:
                        total_distance_meters = getattr(response, "distance_meters", None)
                        total_stops = len(getattr(response, "optimized_stops", []))
                        total_routes = 1
                        solver_type = "LKH_TSP"
                        model_name = "TspResponse"
                    elif req_msg.jobType == JobType.DISTANCE_MATRIX:
                        total_distance_meters = None
                        total_stops = len(getattr(response, "coordinates", []))
                        total_routes = 1
                        matrix_type = MatrixRequest.model_validate(input_dict).matrix_type
                        solver_type = (
                            "OSRM_MATRIX"
                            if matrix_type == MatrixType.STREET
                            else "HAVERSINE_MATRIX"
                        )
                        model_name = "DistanceMatrixResponse"
                    else:
                        routes = getattr(response, "routes", [])
                        total_distance_meters = sum(
                            getattr(r, "distance_meters", 0) for r in routes
                        )
                        total_stops = sum(len(getattr(r, "clients", [])) for r in routes)
                        total_routes = len(routes)

                    result_msg = RoutingResultMessage(
                        routingJobId=req_msg.routingJobId,
                        jobType=req_msg.jobType,
                        inputPath=req_msg.inputPath,
                        outputPath=output_path,
                        durationMillis=elapsed_ms,
                        solverStatus=VrpSolutionStatus.OPTIMAL,
                        userId=req_msg.userId,
                        solverType=solver_type,
                        modelName=model_name,
                        totalDistanceMeters=total_distance_meters,
                        totalStops=total_stops,
                        totalRoutes=total_routes,
                    )

            except InfeasibleVrpError as e:
                elapsed_ms = int((time.time() - start_time) * 1000)
                logger.warning(f"VRP infeasible for job={req_msg.routingJobId if req_msg else None}: {e}")
                result_msg = RoutingResultMessage(
                    routingJobId=req_msg.routingJobId if req_msg else None,
                    jobType=req_msg.jobType if req_msg else JobType.VRP,
                    inputPath=req_msg.inputPath if req_msg else "",
                    durationMillis=elapsed_ms,
                    solverStatus=VrpSolutionStatus.INFEASIBLE,
                    userId=req_msg.userId if req_msg else None,
                    errorMessage=str(e),
                    solverType="VRP",
                    modelName="VrpOut",
                )

            except Exception as e:
                elapsed_ms = int((time.time() - start_time) * 1000)
                logger.error(f"Error processing job: {e}", exc_info=True)
                result_msg = RoutingResultMessage(
                    routingJobId=req_msg.routingJobId if req_msg else None,
                    jobType=req_msg.jobType if req_msg else JobType.VRP,
                    inputPath=req_msg.inputPath if req_msg else "",
                    durationMillis=elapsed_ms,
                    solverStatus=VrpSolutionStatus.ERROR,
                    userId=req_msg.userId if req_msg else None,
                    errorMessage=str(e),
                    solverType="ERROR",
                    modelName="ERROR",
                )

            exchange = await self.channel.get_exchange("routing.exchange")
            await exchange.publish(
                aio_pika.Message(
                    body=result_msg.model_dump_json(by_alias=True).encode(),
                    correlation_id=message.correlation_id,
                    content_type="application/json",
                ),
                routing_key="routing.result",
            )
            logger.info(f"Result {result_msg.solverStatus.value} published for {result_msg.jobType.value}")

    def _get_semaphore(self, job_type: JobType) -> asyncio.Semaphore:
        if self.concurrency_limiter is None:
            from app.main import concurrency_limiter as cl
            self.concurrency_limiter = cl
        if job_type == JobType.TSP:
            return self.concurrency_limiter.tsp_semaphore
        if job_type == JobType.DISTANCE_MATRIX:
            return self.concurrency_limiter.matrix_semaphore
        return self.concurrency_limiter.vrp_semaphore

    async def _solve(self, job_type: JobType, input_dict: dict):
        if job_type == JobType.TSP:
            from app.dtos import TspRequest
            from app.algorithms.tsp.lkh_solver import TspLkhResolver
            tsp_in = TspRequest.model_validate(input_dict)
            return await asyncio.to_thread(TspLkhResolver(tsp_in, settings).resolve)
        elif job_type == JobType.VRP:
            from app.algorithms.vrp.vrp_solver import VrpSolver
            vrp_in = VrpIn.model_validate(input_dict)
            return await asyncio.to_thread(VrpSolver(vrp_in, settings).resolve)
        elif job_type == JobType.DISTANCE_MATRIX:
            from app.algorithms.calculate_distances import calculate_distances
            from app.services.osrm_service import osrm_service
            import numpy as np

            matrix_in = MatrixRequest.model_validate(input_dict)
            start_time = time.time()

            if matrix_in.matrix_type == MatrixType.STREET:
                matrix = await asyncio.to_thread(
                    osrm_service.get_distance_matrix,
                    [(c.lat, c.lng) for c in matrix_in.coordinates],
                )
                if matrix is None:
                    raise RuntimeError("OSRM distance matrix request failed")
            else:
                points = np.array(
                    [(c.lat, c.lng) for c in matrix_in.coordinates], dtype=float
                )
                matrix = await asyncio.to_thread(calculate_distances, points)

            return DistanceMatrixResponse(
                matrix=matrix.tolist(),
                coordinates=matrix_in.coordinates,
                time_to_solve_ms=float((time.time() - start_time) * 1000),
            )
        raise ValueError(f"Unsupported job type: {job_type}")

    async def close(self):
        if self.connection:
            await self.connection.close()
            logger.info("RabbitMQ connection closed.")
