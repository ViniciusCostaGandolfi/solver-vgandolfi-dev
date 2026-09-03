import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import settings
from app.dtos import TspRequest, TspResponse
from app.services.rabbitmq_service import RabbitMQService
from app.services.s3_service import S3Service

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

s3_service = S3Service()
rabbitmq_service = RabbitMQService(s3_service)


class ConcurrencyLimiter:
    def __init__(self):
        self.tsp_semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_TSP)
        self.vrp_semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_VRP)
        self.matrix_semaphore = asyncio.Semaphore(settings.MAX_CONCURRENT_MATRIX)


concurrency_limiter = ConcurrencyLimiter()


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting FazRota Worker...")
    s3_service.check_connection()
    await rabbitmq_service.connect()
    yield
    logger.info("Shutting down FazRota Worker...")
    await rabbitmq_service.close()


app = FastAPI(lifespan=lifespan, title="FazRota Worker", version="1.0.0")


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc):
    logger.error(f"Validation error: {exc.errors()}")
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/logistic/tsp", response_model=TspResponse)
async def tsp_endpoint(request: TspRequest):
    """
    Solve TSP synchronously via LKH heuristic.
    Returns 503 if concurrency limit is reached.
    """
    if concurrency_limiter.tsp_semaphore.locked():
        raise HTTPException(status_code=503, detail="TSP concurrency limit reached. Try again later.")

    async with concurrency_limiter.tsp_semaphore:
        try:
            from app.algorithms.tsp.lkh_solver import TspLkhResolver

            resolver = TspLkhResolver(request, settings)
            response = await asyncio.to_thread(resolver.resolve)
            return response
        except Exception as e:
            logger.error(f"TSP solver error: {e}", exc_info=True)
            raise HTTPException(status_code=500, detail=str(e))
