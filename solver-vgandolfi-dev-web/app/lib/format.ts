import type { PointRow } from "./types";

/* ----------------------------- ids ----------------------------- */

export function uid(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `id-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/* -------------------------- formatação -------------------------- */

export function fmtNumber(n: number, maxDecimals = 2): string {
  if (!Number.isFinite(n)) return "—";
  return n.toLocaleString("pt-BR", { maximumFractionDigits: maxDecimals });
}

export function fmtDistanceMeters(meters?: number | null): string {
  if (meters === null || meters === undefined || !Number.isFinite(meters)) {
    return "—";
  }
  if (meters >= 1000) {
    const km = meters / 1000;
    return `${fmtNumber(km, 2)} km`;
  }
  return `${fmtNumber(meters, 0)} m`;
}

export function fmtDurationMs(ms?: number | null): string {
  if (ms === null || ms === undefined || !Number.isFinite(ms)) return "—";
  if (ms >= 3_600_000) {
    const h = Math.floor(ms / 3_600_000);
    const m = Math.round((ms % 3_600_000) / 60_000);
    return m === 0
      ? `${fmtNumber(h, 0)} h`
      : `${fmtNumber(h, 0)} h ${fmtNumber(m, 0)} min`;
  }
  if (ms >= 60_000) {
    const m = Math.floor(ms / 60_000);
    const s = Math.round((ms % 60_000) / 1_000);
    return s === 0
      ? `${fmtNumber(m, 0)} min`
      : `${fmtNumber(m, 0)} min ${fmtNumber(s, 0)} s`;
  }
  if (ms >= 1_000) return `${fmtNumber(ms / 1_000, 2)} s`;
  return `${fmtNumber(ms, 0)} ms`;
}

export function fmtDateTime(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

/* ------------------------- clipboard ------------------------- */

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand("copy");
      document.body.removeChild(ta);
      return ok;
    } catch {
      return false;
    }
  }
}

/* --------------------- download de arquivos --------------------- */

export function downloadJson(data: unknown, filename: string): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/* ---------------------- importação de arquivos ---------------------- */

export interface ParsedPoints {
  rows: Array<{ name: string; lat: string; lng: string }>;
  sourceLabel: string;
}

/** Faz o split de uma linha CSV respeitando aspas e o separador detectado. */
function parseCsvLine(line: string, sep: string): string[] {
  const cols: string[] = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]!;
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cur += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === sep) {
      cols.push(cur.trim());
      cur = "";
    } else {
      cur += ch;
    }
  }
  cols.push(cur.trim());
  return cols;
}

function parseCsv(text: string): Array<{ name: string; lat: string; lng: string }> {
  // Suporta CRLF e LF, ignorando linhas vazias.
  const lines = text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length === 0) return [];

  // O separador é decidido APENAS pelo cabeçalho (primeira linha).
  const headerLine = lines[0]!;
  const sep =
    headerLine.includes(";") && !headerLine.includes(",") ? ";" : ",";

  const rows: Array<{ name: string; lat: string; lng: string }> = [];

  for (const [i, line] of lines.entries()) {
    const cols = parseCsvLine(line, sep);

    let latIdx = -1;
    let lngIdx = -1;
    let nameIdx = -1;

    if (i === 0) {
      const header = cols.map((c) => c.toLowerCase());
      const find = (...names: string[]) => header.findIndex((h) => names.includes(h));
      latIdx = find("lat", "latitude", "latitud");
      lngIdx = find("lng", "lon", "long", "longitude", "longitud");
      nameIdx = find("name", "nome", "label", "rotulo", "rótulo", "customer_name");
      // sem cabeçalho reconhecido → trata a primeira linha como dado
      if (latIdx === -1 && lngIdx === -1) {
        latIdx = cols.length > 1 ? 0 : -1;
        lngIdx = cols.length > 1 ? 1 : -1;
      }
    } else {
      latIdx = cols.length > 1 ? 0 : -1;
      lngIdx = cols.length > 1 ? 1 : -1;
      nameIdx = cols.length > 2 ? 2 : -1;
    }

    if (latIdx < 0 || lngIdx < 0) continue;

    const lat = cols[latIdx] ?? "";
    const lng = cols[lngIdx] ?? "";
    if (!lat || !lng) continue;

    const name =
      nameIdx >= 0 && cols[nameIdx] ? cols[nameIdx]! : `Ponto ${rows.length + 1}`;
    rows.push({ name, lat, lng });
  }

  return rows;
}

export function parsePointsFile(
  file: File,
): Promise<ParsedPoints> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Não foi possível ler o arquivo."));
    reader.onload = () => {
      try {
        const text = String(reader.result ?? "");
        if (file.name.toLowerCase().endsWith(".csv")) {
          resolve({ rows: parseCsv(text), sourceLabel: file.name });
          return;
        }
        const data = JSON.parse(text) as unknown;
        resolve({ rows: pointsFromJson(data), sourceLabel: file.name });
      } catch (err) {
        reject(
          err instanceof Error ? err : new Error("Arquivo inválido."),
        );
      }
    };
    reader.readAsText(file);
  });
}

function pointsFromJson(
  data: unknown,
): Array<{ name: string; lat: string; lng: string }> {
  const out: Array<{ name: string; lat: string; lng: string }> = [];

  const pushCoord = (lat: unknown, lng: unknown, name?: unknown) => {
    const n = (v: unknown): string | null =>
      v === null || v === undefined || v === "" ? null : String(v);
    const la = n(lat);
    const ln = n(lng);
    if (la !== null && ln !== null) {
      out.push({
        name: n(name) ?? `Ponto ${out.length + 1}`,
        lat: la,
        lng: ln,
      });
    }
  };

  if (Array.isArray(data)) {
    for (const item of data) {
      if (item && typeof item === "object") {
        const o = item as Record<string, unknown>;
        const addr = (o.address ?? {}) as Record<string, unknown>;
        pushCoord(
          o.latitude ?? addr.latitude,
          o.longitude ?? addr.longitude,
          o.name ?? o.customer_name ?? addr.customer_name,
        );
      }
    }
    return out;
  }

  if (data && typeof data === "object") {
    const o = data as Record<string, unknown>;
    for (const key of ["stops", "clients", "coordinates", "points", "locations"]) {
      if (Array.isArray(o[key])) {
        for (const item of o[key] as unknown[]) {
          if (item && typeof item === "object") {
            const it = item as Record<string, unknown>;
            const addr = (it.address ?? {}) as Record<string, unknown>;
            pushCoord(
              it.latitude ?? it.lat ?? addr.latitude,
              it.longitude ?? it.lng ?? addr.longitude,
              it.name ?? it.customer_name ?? addr.customer_name,
            );
          }
        }
        return out;
      }
    }
    // objeto solto com lat/lng
    pushCoord(o.latitude ?? o.lat, o.longitude ?? o.lng, o.name);
  }

  return out;
}

const API_URL: string = import.meta.env.VITE_API_URL ?? "";

/** Constrói a URL absoluta da API para exemplos de curl (usa VITE_API_URL real). */
export function apiAbsoluteUrl(path: string): string {
  if (API_URL) {
    return `${API_URL.replace(/\/$/, "")}${path}`;
  }
  // dev: sem VITE_API_URL → assume o backend local (proxy do Vite)
  return `http://localhost:8080/api/v1${path}`;
}

/** URL do endpoint /health, usando a mesma base da API quando disponível. */
export function apiHealthUrl(): string {
  if (API_URL) {
    try {
      return `${new URL(API_URL).origin}/health`;
    } catch {
      return "/health";
    }
  }
  return "/health";
}

/** URL da documentação interativa (Swagger UI), na mesma origem da API. */
export function apiSwaggerUrl(): string {
  if (API_URL) {
    try {
      return `${new URL(API_URL).origin}/swagger-ui/index.html`;
    } catch {
      return "http://localhost:8080/swagger-ui/index.html";
    }
  }
  return "http://localhost:8080/swagger-ui/index.html";
}

export function pointsFromGeocode(
  address: { formattedAddress: string; latitude: number; longitude: number },
): { name: string; lat: string; lng: string } {
  return {
    name: address.formattedAddress.slice(0, 80),
    lat: String(address.latitude),
    lng: String(address.longitude),
  };
}