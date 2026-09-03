import type {
  GeoCoord,
  MatrixOutput,
  RouteDto,
  TspOutput,
  VrpOutput,
} from "./types";

/* ----------------------------- type guards ----------------------------- */

export function isGeoCoordArray(v: unknown): v is GeoCoord[] {
  return (
    Array.isArray(v) &&
    v.every(
      (c) =>
        !!c &&
        typeof c === "object" &&
        typeof (c as GeoCoord).lat === "number" &&
        typeof (c as GeoCoord).lng === "number",
    )
  );
}

export function isTspOutput(v: unknown): v is TspOutput {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (o.route_line !== undefined && !isGeoCoordArray(o.route_line)) return false;
  if (o.distance_meters !== undefined && typeof o.distance_meters !== "number") {
    return false;
  }
  if (o.optimized_stops !== undefined && !Array.isArray(o.optimized_stops)) {
    return false;
  }
  return true;
}

export function isVrpOutput(v: unknown): v is VrpOutput {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (o.routes !== undefined) {
    if (!Array.isArray(o.routes)) return false;
    for (const r of o.routes) {
      if (!r || typeof r !== "object") return false;
      const rr = r as Record<string, unknown>;
      if (rr.route_line !== undefined && !isGeoCoordArray(rr.route_line)) {
        return false;
      }
    }
  }
  return true;
}

export function isMatrixOutput(v: unknown): v is MatrixOutput {
  if (!v || typeof v !== "object") return false;
  const o = v as Record<string, unknown>;
  if (o.matrix !== undefined) {
    if (!Array.isArray(o.matrix)) return false;
    for (const row of o.matrix) {
      if (!Array.isArray(row)) return false;
      for (const cell of row) {
        if (typeof cell !== "number") return false;
      }
    }
  }
  return true;
}

/** Linha de rota do TSP como pares [lat, lng] válidos. */
export function tspRouteCoords(o: TspOutput): Array<[number, number]> {
  return (o.route_line ?? [])
    .filter((c) => Number.isFinite(c.lat) && Number.isFinite(c.lng))
    .map((c) => [c.lat, c.lng] as [number, number]);
}

/** Rotas (VRP) validadas do output. */
export function vrpRoutes(o: VrpOutput): RouteDto[] {
  return o.routes ?? [];
}

/** Linha de rota de um RouteDto como pares [lat, lng] válidos. */
export function routeDtoCoords(r: RouteDto): Array<[number, number]> {
  return (r.route_line ?? [])
    .filter((c) => Number.isFinite(c.lat) && Number.isFinite(c.lng))
    .map((c) => [c.lat, c.lng] as [number, number]);
}
