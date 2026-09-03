import type {
  GeocodeResult,
  JobResponse,
  JobStatusResponse,
  ProblemType,
} from "./types";

export const API_BASE: string =
  import.meta.env.VITE_API_URL ?? "/api/v1";

export class ApiError extends Error {
  readonly status: number;
  readonly retryAfterSeconds?: number;

  constructor(status: number, message: string, retryAfterSeconds?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

async function parseError(res: Response): Promise<ApiError> {
  let message = `Erro ${res.status} ao falar com a API.`;
  let retryAfterSeconds: number | undefined;

  try {
    const data = (await res.json()) as {
      error?: string;
      retryAfterSeconds?: number;
      message?: string;
    };
    if (data.error) message = data.error;
    if (data.message) message = data.message;
    retryAfterSeconds = data.retryAfterSeconds;
  } catch {
    /* body não é JSON */
  }

  if (res.status === 429) {
    message =
      "Limite de requisições atingido para este IP. Aguarde um instante e tente novamente.";
  }

  return new ApiError(res.status, message, retryAfterSeconds);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  if (!res.ok) throw await parseError(res);

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** Rota do endpoint de criação para cada tipo de problema. */
export function jobTypeRoute(type: ProblemType): string {
  switch (type) {
    case "TSP":
      return "tsp";
    case "VRP":
      return "vrp";
    case "DISTANCE_MATRIX":
      return "distance-matrix";
  }
}

export async function createJob(
  type: ProblemType,
  input: Record<string, unknown>,
  webhookUrl?: string,
): Promise<JobResponse> {
  return request<JobResponse>(`/jobs/${jobTypeRoute(type)}`, {
    method: "POST",
    body: JSON.stringify({
      webhookUrl: webhookUrl || undefined,
      input,
    }),
  });
}

export async function getJobStatus(id: string): Promise<JobStatusResponse> {
  return request<JobStatusResponse>(`/jobs/${encodeURIComponent(id)}`);
}

export async function getJobOutput<T = unknown>(id: string): Promise<T> {
  return request<T>(`/jobs/${encodeURIComponent(id)}/output`);
}

export async function geocode(address: string): Promise<GeocodeResult[]> {
  return request<GeocodeResult[]>(
    `/geo/geocode?address=${encodeURIComponent(address)}`,
  );
}