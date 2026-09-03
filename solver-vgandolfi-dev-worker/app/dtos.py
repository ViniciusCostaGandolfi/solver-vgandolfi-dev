from __future__ import annotations
from datetime import datetime
from enum import Enum
from typing import List, Optional
from uuid import uuid4
from zoneinfo import ZoneInfo
from pydantic import UUID4, BaseModel, ConfigDict, Field


class JobType(str, Enum):
    TSP = "TSP"
    VRP = "VRP"
    DISTANCE_MATRIX = "DISTANCE_MATRIX"


class MatrixType(str, Enum):
    EUCLIDIAN = "EUCLIDIAN"
    STREET = "STREET"


class VrpSolutionStatus(str, Enum):
    OPTIMAL = "OPTIMAL"
    FEASIBLE = "FEASIBLE"
    INFEASIBLE = "INFEASIBLE"
    ERROR = "ERROR"
    RUNNING = "RUNNING"
    TIMEOUT = "TIMEOUT"
    PENDING = "PENDING"
    UNKNOWN = "UNKNOWN"


class Coordinate(BaseModel):
    lat: float
    lng: float


class Address(BaseModel):
    customer_name: str
    street_name: str
    street_number: str
    complement: Optional[str] = None
    neighborhood: Optional[str] = None
    city: str
    state: str
    postal_code: str
    latitude: float
    longitude: float


class TspStop(BaseModel):
    id: str
    items_description: Optional[str] = None
    customer_name: Optional[str] = None
    address: Address


class TspRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: Optional[str] = None
    external_id: Optional[str] = None
    has_public: bool = False
    origin: Address
    stops: List[TspStop]
    matrix_type: MatrixType = Field(default=MatrixType.EUCLIDIAN, alias="matrixType")


class TspResponse(BaseModel):
    optimized_stops: List[TspStop]
    route_line: List[Coordinate]
    distance_meters: float
    time_to_solve_ms: float


class VehicleType(BaseModel):
    id: UUID4 = Field(default_factory=uuid4)
    name: str
    max_volume_liters: Optional[float] = None
    max_weight_kg: Optional[float] = None
    max_deliveries: Optional[int] = None
    max_distance_meters: Optional[float] = None
    min_routes: int = 0
    max_routes: Optional[int] = None
    target_proportion: Optional[float] = None
    fixed_cost: float = 0.0


class Client(BaseModel):
    id: UUID4 = Field(default_factory=uuid4)
    external_id: Optional[str] = None
    customer_name: Optional[str] = None
    volume_liters: float
    weight_kg: float
    created_at: int = 0
    delivered_until: Optional[int] = None
    address: Address


class VrpIn(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: UUID4 = Field(default_factory=uuid4)
    origin: Address
    clients: List[Client]
    vehicles: List[VehicleType]
    matrix_type: MatrixType = Field(default=MatrixType.EUCLIDIAN, alias="matrixType")
    force_route_count: Optional[int] = None
    max_route_distance: Optional[float] = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(ZoneInfo("America/Sao_Paulo")))


class RoutingRequestMessage(BaseModel):
    routingJobId: UUID4
    jobType: JobType
    inputPath: str
    userId: UUID4
    webhookUrl: Optional[str] = None


class RoutingResultMessage(BaseModel):
    routingJobId: UUID4
    jobType: JobType
    inputPath: str
    outputPath: Optional[str] = None
    durationMillis: Optional[int] = None
    solverStatus: VrpSolutionStatus
    errorMessage: Optional[str] = None
    warningMessage: Optional[str] = None
    solverType: str = "LARGE_VRP"
    modelName: str = "VrpOut"
    userId: UUID4
    totalDistanceMeters: Optional[float] = None
    totalStops: Optional[int] = None
    totalRoutes: Optional[int] = None


class RouteDto(BaseModel):
    """VRP route output — a single vehicle's route."""
    vehicle_id: Optional[UUID4] = None
    vehicle_name: Optional[str] = None
    clients: List[Client]
    route_line: List[Coordinate]
    distance_meters: float
    volume_liters: float
    weight_kg: float
    route_deliveries: int


class VrpOut(BaseModel):
    """VRP solver output."""
    id: UUID4
    origin: Address
    routes: List[RouteDto]
    created_at: datetime
    time_to_solve_ms: float


class MatrixRequest(BaseModel):
    """Distance-matrix request payload (read from S3 `inputPath`).

    The orchestrator sends the payload in camelCase (`matrixType`); the
    snake_case field name is also accepted for backwards compatibility.
    """

    model_config = ConfigDict(populate_by_name=True)

    coordinates: List[Coordinate] = Field(min_length=2, max_length=500)
    matrix_type: MatrixType = Field(default=MatrixType.EUCLIDIAN, alias="matrixType")


class DistanceMatrixResponse(BaseModel):
    """Distance-matrix solver output, distances in meters."""
    matrix: List[List[float]]
    coordinates: List[Coordinate]
    time_to_solve_ms: float
