import type { MatrixType, PointRow, VehicleRow } from "./types";

export interface OriginState {
  name: string;
  lat: string;
  lng: string;
}

/** Limites de pontos alinhados ao backend (@Size do DTO). */
export const MAX_POINTS_TSP_VRP = 100;
export const MAX_POINTS_MATRIX = 500;

/** Erro tipado de payload inválido. */
export class PayloadError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "PayloadError";
  }
}

export function parseCoord(value: string): number | null {
  if (!value) return null;
  const n = Number(String(value).trim().replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

export function isValidLatLng(lat: number | null, lng: number | null): boolean {
  if (lat === null || lng === null) return false;
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

function requireCoord(value: string, label: string): number {
  const n = parseCoord(value);
  if (n === null) {
    throw new PayloadError(`Coordenada inválida para "${label}".`);
  }
  return n;
}

function buildLocation(lat: number, lng: number) {
  return { lat, lng };
}

export function buildTspInput(
  origin: OriginState,
  stops: PointRow[],
  matrixType: MatrixType,
): Record<string, unknown> {
  const originLat = requireCoord(origin.lat, "origem");
  const originLng = requireCoord(origin.lng, "origem");
  return {
    matrixType,
    origin: buildLocation(originLat, originLng),
    stops: stops.map((s, i) => ({
      id: s.id,
      name: s.name || `Parada ${i + 1}`,
      location: buildLocation(
        requireCoord(s.lat, s.name || `parada ${i + 1}`),
        requireCoord(s.lng, s.name || `parada ${i + 1}`),
      ),
    })),
  };
}

export function buildVrpInput(
  origin: OriginState,
  clients: PointRow[],
  vehicles: VehicleRow[],
  matrixType: MatrixType,
): Record<string, unknown> {
  const originLat = requireCoord(origin.lat, "origem");
  const originLng = requireCoord(origin.lng, "origem");
  return {
    matrixType,
    origin: buildLocation(originLat, originLng),
    clients: clients.map((c, i) => ({
      id: c.id,
      name: c.name || `Cliente ${i + 1}`,
      location: buildLocation(
        requireCoord(c.lat, c.name || `cliente ${i + 1}`),
        requireCoord(c.lng, c.name || `cliente ${i + 1}`),
      ),
      volumeLiters: parseCoord(c.volumeLiters ?? "0") ?? 0,
      weightKg: parseCoord(c.weightKg ?? "0") ?? 0,
    })),
    vehicles: vehicles.map((v) => ({
      name: v.name,
      maxDeliveries: parseCoord(v.maxDeliveries) ?? 0,
      maxWeightKg: parseCoord(v.maxWeightKg) ?? 0,
      maxVolumeLiters: parseCoord(v.maxVolumeLiters ?? "0") ?? 0,
    })),
  };
}

export function buildMatrixInput(
  coordinates: PointRow[],
  matrixType: MatrixType,
): Record<string, unknown> {
  return {
    matrixType,
    coordinates: coordinates.map((c, i) => ({
      lat: requireCoord(c.lat, `coordenada ${i + 1}`),
      lng: requireCoord(c.lng, `coordenada ${i + 1}`),
    })),
  };
}

/** Valida o scheme do webhook (http/https). Retorna mensagem ou null se OK. */
export function validateWebhookUrl(url: string): string | null {
  const trimmed = url.trim();
  if (!trimmed) return null;
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol === "http:" || parsed.protocol === "https:") {
      return null;
    }
  } catch {
    /* cai no erro abaixo */
  }
  return "O webhook precisa ser uma URL válida com http:// ou https://.";
}

/** Valida os campos essenciais e devolve uma mensagem amigável, ou null se OK. */
export function validateProblem(
  type: "TSP" | "VRP" | "DISTANCE_MATRIX",
  origin: OriginState,
  points: PointRow[],
  vehicles: VehicleRow[],
  webhookUrl = "",
): string | null {
  if (type !== "DISTANCE_MATRIX") {
    const olat = parseCoord(origin.lat);
    const olng = parseCoord(origin.lng);
    if (!isValidLatLng(olat, olng)) {
      return "Informe uma origem válida (latitude e longitude).";
    }
  }

  if (points.length < 2 && type === "DISTANCE_MATRIX") {
    return "A matriz de distâncias precisa de pelo menos 2 pontos.";
  }
  if (points.length < 2 && type === "TSP") {
    return "O TSP precisa de pelo menos 2 paradas além da origem.";
  }
  if (points.length < 1 && type === "VRP") {
    return "Adicione pelo menos 1 cliente para roteirizar.";
  }

  const maxPoints = type === "DISTANCE_MATRIX" ? MAX_POINTS_MATRIX : MAX_POINTS_TSP_VRP;
  if (points.length > maxPoints) {
    return `Máximo de ${maxPoints} pontos permitidos. Remova alguns para continuar.`;
  }

  for (const p of points) {
    if (!isValidLatLng(parseCoord(p.lat), parseCoord(p.lng))) {
      return `O ponto "${p.name || "sem nome"}" está com coordenadas inválidas.`;
    }
  }

  if (type === "VRP") {
    if (vehicles.length === 0) {
      return "Adicione pelo menos 1 veículo.";
    }
    for (const v of vehicles) {
      if (!v.name.trim()) return "Dê um nome para todos os veículos.";

      const maxDeliveries = parseCoord(v.maxDeliveries);
      if (maxDeliveries === null || maxDeliveries <= 0) {
        return `O veículo "${v.name || "sem nome"}" precisa de um número máximo de paradas maior que zero.`;
      }

      const maxWeightKg = parseCoord(v.maxWeightKg);
      if (maxWeightKg === null || maxWeightKg <= 0) {
        return `O veículo "${v.name || "sem nome"}" precisa de um peso máximo maior que zero.`;
      }

      const maxVolumeLiters = parseCoord(v.maxVolumeLiters ?? "0") ?? 0;
      if (maxVolumeLiters < 0) {
        return `O veículo "${v.name || "sem nome"}" não pode ter volume máximo negativo.`;
      }
    }

    for (const c of points) {
      const volume = parseCoord(c.volumeLiters ?? "0") ?? 0;
      const weight = parseCoord(c.weightKg ?? "0") ?? 0;
      if (volume < 0) {
        return `O cliente "${c.name || "sem nome"}" não pode ter volume negativo.`;
      }
      if (weight < 0) {
        return `O cliente "${c.name || "sem nome"}" não pode ter peso negativo.`;
      }
    }
  }

  const webhookErr = validateWebhookUrl(webhookUrl);
  if (webhookErr) return webhookErr;

  return null;
}
