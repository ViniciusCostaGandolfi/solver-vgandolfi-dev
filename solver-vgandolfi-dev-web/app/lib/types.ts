export type ProblemType = "TSP" | "VRP" | "DISTANCE_MATRIX";

export type JobStatusValue = "PENDING" | "RUNNING" | "DONE" | "ERROR";

export type MatrixType = "EUCLIDIAN" | "STREET";

export interface JobResponse {
  id: string;
  type: ProblemType;
  status: JobStatusValue;
  inputUrl?: string | null;
  outputUrl?: string | null;
  statusUrl?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  processingTimeMs?: number | null;
}

export interface JobStatusResponse {
  id: string;
  type: ProblemType;
  status: JobStatusValue;
  inputUrl?: string | null;
  outputUrl?: string | null;
  statusUrl?: string | null;
  webhookUrl?: string | null;
  errorMessage?: string | null;
  processingTimeMs?: number | null;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  inputPath?: string | null;
  outputPath?: string | null;
}

export interface GeocodeResult {
  formattedAddress: string;
  streetName?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  latitude: number;
  longitude: number;
  source?: string | null;
}

/** Ponto editável na tabela da UI. lat/lng ficam como string enquanto o usuário edita. */
export interface PointRow {
  id: string;
  name: string;
  lat: string;
  lng: string;
  volumeLiters?: string;
  weightKg?: string;
}

export interface VehicleRow {
  id: string;
  name: string;
  maxDeliveries: string;
  maxWeightKg: string;
  maxVolumeLiters?: string;
}

/* ------------------------- Outputs do solver ------------------------- */

export interface GeoCoord {
  lat: number;
  lng: number;
}

export interface TspOutput {
  optimized_stops?: Array<{
    id?: string;
    customer_name?: string | null;
    address?: { latitude?: number; longitude?: number };
  }>;
  route_line?: GeoCoord[];
  distance_meters?: number;
  time_to_solve_ms?: number;
}

export interface RouteDto {
  vehicle_id?: string | null;
  vehicle_name?: string | null;
  clients?: Array<{ customer_name?: string | null }>;
  route_line?: GeoCoord[];
  distance_meters?: number;
  volume_liters?: number;
  weight_kg?: number;
  route_deliveries?: number;
}

export interface VrpOutput {
  routes?: RouteDto[];
  origin?: GeoCoord;
  time_to_solve_ms?: number;
}

export interface MatrixOutput {
  matrix?: number[][];
  paths?: { lat: number; lng: number }[][][];
  coordinates?: GeoCoord[];
  time_to_solve_ms?: number;
}

export type SolverOutput = TspOutput | VrpOutput | MatrixOutput;