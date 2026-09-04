import { useEffect, useState } from "react";

import tsp1 from "~/assets/examples/tsp-1.json";
import tsp2 from "~/assets/examples/tsp-2.json";
import tsp3 from "~/assets/examples/tsp-3.json";
import vrp1 from "~/assets/examples/vrp-1.json";
import vrp2 from "~/assets/examples/vrp-2.json";
import vrp3 from "~/assets/examples/vrp-3.json";
import { fmtDistanceMeters, fmtDurationMs } from "~/lib/format";

import { IconArrowRight, IconCheck, IconRoute, IconTerminal, IconZap } from "./icons";

const VIEW_W = 320;
const VIEW_H = 180;
const CAROUSEL_MS = 3000;

type LatLng = { lat: number; lng: number };

/* Tipos locais dos exemplos estáticos — subset dos campos usados no preview
   (os JSONs têm mais campos; a tipagem via resolveJsonModule é compatível). */
interface HeroTspExample {
  optimized_stops: Array<{
    id?: string;
    customer_name?: string | null;
    address: { latitude: number; longitude: number };
  }>;
  route_line: Array<{ lat: number; lng: number }>;
  distance_meters: number;
  _meta?: { n_stops?: number };
}

interface HeroVrpExample {
  origin: { latitude: number; longitude: number };
  routes: Array<{
    vehicle_id?: string | null;
    vehicle_name?: string | null;
    clients: Array<{
      id?: string;
      customer_name?: string | null;
      address: { latitude: number; longitude: number };
    }>;
    route_line: Array<{ lat: number; lng: number }>;
  }>;
  time_to_solve_ms: number;
  _meta?: { n_clients?: number; n_routes?: number };
}

/**
 * Projeta coordenadas reais (lat/lng) para o viewBox do SVG (320×180),
 * preservando a proporção e centralizando os pontos.
 */
function makeProjector(points: LatLng[]) {
  const lats = points.map((p) => p.lat);
  const lngs = points.map((p) => p.lng);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const rLat = Math.max(maxLat - minLat, 1e-6);
  const rLng = Math.max(maxLng - minLng, 1e-6);
  const pad = 24;
  const scale = Math.min((VIEW_W - pad * 2) / rLng, (VIEW_H - pad * 2) / rLat);
  const ox = (VIEW_W - rLng * scale) / 2;
  const oy = (VIEW_H - rLat * scale) / 2;
  return (p: LatLng): { x: number; y: number } => ({
    x: ox + (p.lng - minLng) * scale,
    y: oy + (maxLat - p.lat) * scale,
  });
}

function Marker({
  x,
  y,
  label,
  color,
  isOrigin = false,
  radius,
  strokeWidth = 2.5,
}: {
  x: number;
  y: number;
  label: string;
  color: string;
  isOrigin?: boolean;
  radius?: number;
  strokeWidth?: number;
}) {
  const r = radius ?? (isOrigin ? 9 : 7);
  return (
    <g>
      <circle
        cx={x}
        cy={y}
        r={r}
        fill={isOrigin ? color : "var(--color-base-100)"}
        stroke={color}
        strokeWidth={strokeWidth}
      />
      {label && (
        <text
          x={x}
          y={y}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize="8"
          fontWeight="700"
          fill={isOrigin ? "var(--color-accent-content)" : color}
        >
          {label}
        </text>
      )}
    </g>
  );
}

const ORIGIN_COLOR = "#f59e0b";
const TSP_COLOR = "#6366f1";
/** Paleta real de rotas do mapa (ROUTE_COLORS do MapCanvas) — cobre até 5 rotas sem repetir. */
const VRP_ROUTE_COLORS = [
  "#6366f1",
  "#0ea5e9",
  "#f59e0b",
  "#10b981",
  "#ef4444",
  "#a855f7",
  "#ec4899",
  "#84cc16",
];

function TspPreview({ data }: { data: HeroTspExample }) {
  const routePts = data.route_line;
  const project = makeProjector(routePts);
  const origin = project(routePts[0]);
  const stops = data.optimized_stops.map((s, i) => {
    const p = project({ lat: s.address.latitude, lng: s.address.longitude });
    return { key: s.id ?? String(i), label: String(i + 1), x: p.x, y: p.y };
  });
  const path =
    "M " +
    routePts
      .map((p) => {
        const c = project(p);
        return `${c.x},${c.y}`;
      })
      .join(" L ");
  const nStops = data._meta?.n_stops ?? data.optimized_stops.length;

  return (
    <>
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        className="w-full"
        role="img"
        aria-label="Prévia de rota TSP otimizada"
      >
        <path
          d={path}
          fill="none"
          stroke={TSP_COLOR}
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="animate-route-dash"
          opacity="0.85"
        />
        <Marker x={origin.x} y={origin.y} label="O" color={ORIGIN_COLOR} isOrigin />
        {stops.map((s) => (
          <Marker key={s.key} x={s.x} y={s.y} label={s.label} color={TSP_COLOR} />
        ))}
      </svg>
      <div className="mt-2 flex items-center justify-between text-xs text-base-content/50">
        <span className="font-mono">
          ≈ {fmtDistanceMeters(data.distance_meters)}
        </span>
        <span>{nStops} paradas + origem</span>
      </div>
    </>
  );
}

function VrpPreview({ data }: { data: HeroVrpExample }) {
  const originLL: LatLng = {
    lat: data.origin.latitude,
    lng: data.origin.longitude,
  };
  const allRoutePts = data.routes.flatMap((r) => r.route_line);
  const allClients = data.routes.flatMap((r) => r.clients);
  const project = makeProjector([originLL, ...allRoutePts]);
  const origin = project(originLL);
  const vehicleWord = data.routes.length === 1 ? "veículo" : "veículos";
  /* Com muitos clientes, reduz o marcador e omite o rótulo para não poluir. */
  const dense = allClients.length > 25;

  return (
    <>
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        className="w-full"
        role="img"
        aria-label="Prévia de rota VRP otimizada"
      >
        {data.routes.map((route, i) => {
          const color = VRP_ROUTE_COLORS[i % VRP_ROUTE_COLORS.length]!;
          const path =
            "M " +
            route.route_line
              .map((p) => {
                const c = project(p);
                return `${c.x},${c.y}`;
              })
              .join(" L ");
          return (
            <g key={route.vehicle_id ?? `route-${i}`}>
              <path
                d={path}
                fill="none"
                stroke={color}
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="animate-route-dash"
                opacity="0.85"
              />
              {route.clients.map((c, ci) => {
                const p = project({
                  lat: c.address.latitude,
                  lng: c.address.longitude,
                });
                return (
                  <Marker
                    key={c.id ?? ci}
                    x={p.x}
                    y={p.y}
                    label={dense ? "" : String(ci + 1)}
                    color={color}
                    radius={dense ? 5 : undefined}
                    strokeWidth={dense ? 2 : 2.5}
                  />
                );
              })}
            </g>
          );
        })}
        <Marker x={origin.x} y={origin.y} label="O" color={ORIGIN_COLOR} isOrigin />
      </svg>
      <div className="mt-2 flex items-center justify-between text-xs text-base-content/50">
        <span>
          {data.routes.length} {vehicleWord} · {allClients.length} clientes
        </span>
        <span className="font-mono">
          ≈ {fmtDurationMs(data.time_to_solve_ms)}
        </span>
      </div>
    </>
  );
}

type Slide =
  | { kind: "TSP"; data: HeroTspExample }
  | { kind: "VRP"; data: HeroVrpExample };

/* Alterna TSP/VRP em tamanho crescente, variando cor e densidade a cada slide.
   Os exemplos usam pontos espalhados da Grande SP (sem sobreposição). */
const SLIDES: Slide[] = [
  { kind: "TSP", data: tsp1 },
  { kind: "VRP", data: vrp1 },
  { kind: "TSP", data: tsp2 },
  { kind: "VRP", data: vrp2 },
  { kind: "TSP", data: tsp3 },
  { kind: "VRP", data: vrp3 },
];

function slideTitle(slide: Slide): string {
  if (slide.kind === "TSP") {
    const n = slide.data._meta?.n_stops ?? slide.data.optimized_stops.length;
    return `TSP · ${n} pontos`;
  }
  const clients =
    slide.data._meta?.n_clients ??
    slide.data.routes.reduce((sum, r) => sum + r.clients.length, 0);
  return `VRP · ${clients} clientes`;
}

function RoutePreview() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const id = setInterval(
      () => setIndex((i) => (i + 1) % SLIDES.length),
      CAROUSEL_MS,
    );
    return () => clearInterval(id);
  }, []);

  const slide = SLIDES[index];

  return (
    <div className="card card-border relative overflow-hidden">
      <div className="absolute inset-0 bg-dot-grid opacity-[0.08]" />
      <div key={index} className="card-body relative animate-fade-in">
        <div className="mb-2 flex items-center justify-between">
          <span className="badge badge-ghost badge-sm font-mono">
            {slideTitle(slide)}
          </span>
          <span className="badge badge-success badge-soft badge-sm">
            <IconCheck width={12} height={12} /> otimizado
          </span>
        </div>
        {slide.kind === "TSP" ? (
          <TspPreview data={slide.data} />
        ) : (
          <VrpPreview data={slide.data} />
        )}
      </div>
    </div>
  );
}

export function Hero() {
  return (
    <section id="top" className="relative overflow-hidden">
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 pb-20 pt-16 sm:px-6 lg:grid-cols-[1.1fr,0.9fr] lg:pb-28 lg:pt-24">
        <div className="animate-fade-up">
          <span className="badge badge-ghost badge-lg gap-2 border-base-300 font-mono text-xs text-base-content/70">
            <IconZap width={13} height={13} className="text-primary" />
            otimização gratuita de rotas
          </span>
          <h1 className="mt-5 font-display text-4xl font-bold leading-[1.08] tracking-tight sm:text-5xl lg:text-6xl">
            Resolva rotas de entrega{" "}
            <span className="text-gradient">em segundos</span>
          </h1>
          <p className="mt-5 max-w-xl text-base leading-relaxed text-base-content/65 sm:text-lg">
            Informe os pontos, escolha entre TSP, VRP ou matriz de distâncias
            e receba o resultado otimizado direto no navegador — com mapa,
            resumo e JSON para integrar. Sem cadastro.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <a href="#criar" className="btn btn-primary btn-lg">
              Criar uma rota
              <IconArrowRight width={18} height={18} />
            </a>
            <a href="#api" className="btn btn-outline btn-lg">
              <IconTerminal width={18} height={18} />
              Usar via API
            </a>
          </div>
          <div className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-xs text-base-content/50">
            <span className="flex items-center gap-1.5">
              <IconCheck width={13} height={13} className="text-success" />
              TSP, VRP e matriz de distâncias
            </span>
            <span className="flex items-center gap-1.5">
              <IconCheck width={13} height={13} className="text-success" />
              Sem cadastro
            </span>
            <span className="flex items-center gap-1.5">
              <IconCheck width={13} height={13} className="text-success" />
              Resultado em JSON
            </span>
          </div>
        </div>

        <div
          className="animate-fade-up hidden lg:block"
          style={{ animationDelay: "140ms" }}
        >
          <RoutePreview />
        </div>
      </div>
    </section>
  );
}
