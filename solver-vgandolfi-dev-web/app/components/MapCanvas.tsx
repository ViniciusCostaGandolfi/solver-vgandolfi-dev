import { useEffect, useRef, useState } from "react";
import type {
  Map as LeafletMap,
  Marker as LeafletMarker,
  Polyline as LeafletPolyline,
  TileLayer as LeafletTileLayer,
} from "leaflet";
import { IconPlus } from "./icons";

export interface MapPoint {
  id: string;
  name: string;
  lat: number;
  lng: number;
  kind?: "origin" | "stop";
}

export interface MapRoute {
  id: string;
  coords: Array<[number, number]>;
  color?: string;
}

interface MapCanvasProps {
  points: MapPoint[];
  routes?: MapRoute[];
  dark?: boolean;
  onPointChange?: (id: string, lat: number, lng: number) => void;
  onMapClick?: (lat: number, lng: number) => void;
  className?: string;
  minHeight?: number;
}

const ROUTE_COLORS = [
  "#6366f1",
  "#0ea5e9",
  "#f59e0b",
  "#10b981",
  "#ef4444",
  "#a855f7",
  "#ec4899",
  "#84cc16",
];

function routeColor(index: number): string {
  return ROUTE_COLORS[index % ROUTE_COLORS.length]!;
}

/* Marcador da origem com o mesmo desenho da logo do site (IconRoute):
   dois círculos conectados por um caminho. */
const ORIGIN_ICON_HTML = `
  <div class="solver-marker solver-marker--origin">
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" stroke-width="2" stroke-linecap="round"
      stroke-linejoin="round" width="18" height="18" aria-hidden="true">
      <circle cx="6" cy="19" r="3"/>
      <circle cx="18" cy="5" r="3"/>
      <path d="M12 19h4.5a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h2"/>
    </svg>
  </div>`;

/**
 * Mapa Leaflet controlado por props. Usa `divIcon` customizado (cores do tema),
 * marcadores arrastáveis e polylines de rota. Leaflet é importado dinamicamente
 * para não quebrar o SSR.
 */
export function MapCanvas({
  points,
  routes,
  dark = false,
  onPointChange,
  onMapClick,
  className = "",
  minHeight = 400,
}: MapCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<LeafletMap | null>(null);
  const leafletRef = useRef<typeof import("leaflet") | null>(null);
  const tileLayerRef = useRef<LeafletTileLayer | null>(null);
  const markersRef = useRef<Map<string, LeafletMarker>>(new Map());
  const polylinesRef = useRef<Map<string, LeafletPolyline>>(new Map());
  const [ready, setReady] = useState(false);

  /* Guarda contra clique-acidental após arrastar um marcador: Leaflet pode
     disparar "click" no mapa logo depois de um drag. Um timer de segurança
     garante que a guarda nunca fique presa em `true` (ex.: se o dragend do
     Leaflet não disparar por alguma exceção interna). */
  const dragActiveRef = useRef(false);
  const dragGuardTimerRef = useRef<number | null>(null);

  const onPointChangeRef = useRef(onPointChange);
  const onMapClickRef = useRef(onMapClick);
  useEffect(() => {
    onPointChangeRef.current = onPointChange;
    onMapClickRef.current = onMapClick;
  }, [onPointChange, onMapClick]);

  /* Inicializa o mapa uma única vez */
  useEffect(() => {
    let cancelled = false;
    const container = containerRef.current;
    if (!container) return;

    void (async () => {
      const L = await import("leaflet");
      if (cancelled || !container) return;
      leafletRef.current = L;

      const map = L.map(container, {
        zoomControl: true,
        attributionControl: true,
        minZoom: 2,
      }).setView([-14.2, -51.9], 4);
      mapRef.current = map;

      /* Clique no mapa adiciona ponto APENAS quando o modo adicionar está
         ativo (onMapClick presente). O handler é sempre registrado e decide
         pela ref — assim alternar o modo não exige re-inicializar o mapa. */
      map.on("click", (e) => {
        if (dragActiveRef.current) return;
        onMapClickRef.current?.(e.latlng.lat, e.latlng.lng);
      });

      setReady(true);
    })();

    return () => {
      cancelled = true;
      if (dragGuardTimerRef.current !== null) {
        window.clearTimeout(dragGuardTimerRef.current);
        dragGuardTimerRef.current = null;
      }
      markersRef.current.forEach((m) => m.remove());
      markersRef.current.clear();
      polylinesRef.current.forEach((p) => p.remove());
      polylinesRef.current.clear();
      mapRef.current?.remove();
      mapRef.current = null;
      tileLayerRef.current = null;
      setReady(false);
    };
  }, []);

  /* Troca o tile layer conforme o tema */
  useEffect(() => {
    const L = leafletRef.current;
    const map = mapRef.current;
    if (!L || !map) return;

    tileLayerRef.current?.remove();
const url = dark
      ? "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
      : "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png";
    tileLayerRef.current = L.tileLayer(url, {
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains: "abcd",
      maxZoom: 19,
    }).addTo(map);
  }, [dark, ready]);

  /* Mantém o mapa com o tamanho correto quando o container muda de tamanho
     (layout responsivo, card sticky, etc.). Sem `invalidateSize`, o Leaflet
     continua desenhando com o tamanho antigo e o mapa pode "sumir" ou ficar
     com a área em branco. */
  useEffect(() => {
    const container = containerRef.current;
    const map = mapRef.current;
    if (!container || !map) return;

    let raf = 0;
    const ro = new ResizeObserver(() => {
      if (raf) return;
      raf = window.requestAnimationFrame(() => {
        raf = 0;
        mapRef.current?.invalidateSize();
      });
    });
    ro.observe(container);

    return () => {
      ro.disconnect();
      if (raf) window.cancelAnimationFrame(raf);
    };
  }, [ready]);

  /* Sincroniza marcadores com os pontos */
  useEffect(() => {
    const L = leafletRef.current;
    const map = mapRef.current;
    if (!L || !map || !ready) return;

    const ids = new Set(points.map((p) => p.id));

    for (const [id, marker] of markersRef.current) {
      if (!ids.has(id)) {
        marker.remove();
        markersRef.current.delete(id);
      }
    }

    let stopIndex = 0;
    for (const p of points) {
      const isOrigin = p.kind === "origin";
      const existing = markersRef.current.get(p.id);
      if (existing) {
        const pos = existing.getLatLng();
        if (
          Math.abs(pos.lat - p.lat) > 1e-9 ||
          Math.abs(pos.lng - p.lng) > 1e-9
        ) {
          existing.setLatLng([p.lat, p.lng]);
        }
        /* Reforça a ordem de desenho mesmo para marcadores já criados. */
        if (isOrigin) existing.setZIndexOffset(1000);
        existing.setTooltipContent(p.name || (isOrigin ? "Origem" : ""));
      } else {
        try {
          const html = isOrigin
            ? ORIGIN_ICON_HTML
            : `<div class="solver-marker">${stopIndex + 1}</div>`;
          const size = isOrigin ? [34, 34] : [26, 26];
          const anchor = isOrigin ? [17, 17] : [13, 13];

          const icon = L.divIcon({
            className: "solver-marker-wrap",
            html,
            iconSize: size as [number, number],
            iconAnchor: anchor as [number, number],
          });

          const marker = L.marker([p.lat, p.lng], {
            icon,
            draggable: Boolean(onPointChangeRef.current),
            title: p.name || (isOrigin ? "Origem" : "Ponto"),
            bubblingMouseEvents: false,
            /* A origem fica SEMPRE por cima das bolinhas de parada, mesmo
               quando os marcadores se sobrepõem: o Leaflet ordena por
               `pos.y + zIndexOffset`. */
            zIndexOffset: isOrigin ? 1000 : 0,
          });

          if (!isOrigin) {
            marker.bindTooltip(
              p.name || `Ponto ${stopIndex + 1}`,
              { direction: "top", offset: [0, -14] },
            );
          } else {
            marker.bindTooltip("Origem", { direction: "top", offset: [0, -20] });
          }

          marker.on("dragstart", () => {
            dragActiveRef.current = true;
            if (dragGuardTimerRef.current !== null) {
              window.clearTimeout(dragGuardTimerRef.current);
            }
            dragGuardTimerRef.current = window.setTimeout(() => {
              dragActiveRef.current = false;
            }, 1200);
          });

          marker.on("dragend", (e) => {
            if (dragGuardTimerRef.current !== null) {
              window.clearTimeout(dragGuardTimerRef.current);
              dragGuardTimerRef.current = null;
            }
            const latlng = (e.target as LeafletMarker).getLatLng();
            try {
              onPointChangeRef.current?.(p.id, latlng.lat, latlng.lng);
            } finally {
              /* Mantém a guarda por um instante para ignorar o "click" que o
                 Leaflet pode emitir no mapa logo após soltar o marcador. */
              window.setTimeout(() => {
                dragActiveRef.current = false;
              }, 250);
            }
          });

          marker.addTo(map);
          markersRef.current.set(p.id, marker);
        } catch {
          /* Nunca deixar um marcador inválido quebrar a sincronização do
             mapa inteiro: pula e segue para os demais pontos. */
        }
      }
      if (!isOrigin) stopIndex += 1;
    }
  }, [points, ready]);

  /* Sincroniza polylines de rota */
  useEffect(() => {
    const L = leafletRef.current;
    const map = mapRef.current;
    if (!L || !map || !ready) return;

    const routeList = routes ?? [];
    const ids = new Set(routeList.map((r) => r.id));

    for (const [id, line] of polylinesRef.current) {
      if (!ids.has(id)) {
        line.remove();
        polylinesRef.current.delete(id);
      }
    }

    for (const [i, route] of routeList.entries()) {
      const existing = polylinesRef.current.get(route.id);
      const color = route.color ?? routeColor(i);
      if (existing) {
        existing.setLatLngs(route.coords);
      } else {
        const poly = L.polyline(route.coords, {
          color,
          weight: 4,
          opacity: 0.9,
          lineCap: "round",
          lineJoin: "round",
        }).addTo(map);
        polylinesRef.current.set(route.id, poly);
      }
    }
  }, [routes, ready]);

  /* Ajusta o enquadramento quando o conjunto de pontos muda ou há resultado.
     Protegido contra `fitBounds` com coords repetidas/limites degenerados:
     nesses casos o Leaflet pode derivar zoom NaN/Infinito e deixar o mapa
     "morto" (sem tiles). Com pontos idênticos, preferimos `setView`. */
  const pointsKey = points.map((p) => p.id).join("|");
  const routesKey = (routes ?? [])
    .map((r) => `${r.id}:${r.coords.length}`)
    .join("|");

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;

    /* Se o container não tem tamanho (ex.: colapsou durante re-render),
       invalida o tamanho do Leaflet e evita enquadrar com geometria quebrada. */
    const size = map.getSize();
    if (size.x <= 0 || size.y <= 0) {
      map.invalidateSize();
      return;
    }

    const valid = points.filter(
      (p) =>
        Number.isFinite(p.lat) &&
        Number.isFinite(p.lng) &&
        p.lat >= -90 &&
        p.lat <= 90 &&
        p.lng >= -180 &&
        p.lng <= 180,
    );

    /* Remove coordenadas exatamente duplicadas: fitBounds com pontos
       idênticos não tem extensão real e pode quebrar o mapa. */
    const distinct: Array<[number, number]> = [];
    for (const p of valid) {
      if (
        !distinct.some(([lat, lng]) => lat === p.lat && lng === p.lng)
      ) {
        distinct.push([p.lat, p.lng]);
      }
    }

    const routeCoords = (routes ?? []).flatMap((r) => r.coords);
    const routeDistinct = Array.from(
      new Map(routeCoords.map((c) => [`${c[0]}:${c[1]}`, c])).values(),
    );

    try {
      if (routeDistinct.length >= 2) {
        map.fitBounds(routeDistinct, { padding: [48, 48] });
      } else if (distinct.length >= 2) {
        map.fitBounds(distinct, { padding: [48, 48] });
      } else if (valid.length === 1) {
        map.setView([valid[0]!.lat, valid[0]!.lng], 12);
      }
    } catch {
      /* Nunca deixar um enquadramento inválido derrubar a sincronização:
         em caso de erro, mantém a visão atual do mapa. */
    }
  }, [pointsKey, routesKey, ready]);

  return (
    <div className={`${className} ${onMapClick ? "cursor-crosshair" : ""}`}>
      <div
        ref={containerRef}
        className="solver-map relative overflow-hidden rounded-box"
        style={{ minHeight }}
        role="application"
        aria-label="Mapa com os pontos informados"
      >
        {!ready && (
          <div className="absolute inset-0 z-[500] grid place-items-center bg-base-200">
            <div className="flex flex-col items-center gap-3">
              <span className="loading loading-spinner loading-lg text-primary" />
              <p className="text-xs text-base-content/60">Carregando mapa…</p>
            </div>
          </div>
        )}
        {ready && onMapClick && (
          <div className="pointer-events-none absolute bottom-3 left-1/2 z-[500] flex -translate-x-1/2 items-center gap-1.5 whitespace-nowrap rounded-full bg-primary px-3 py-1 text-xs font-medium text-primary-content shadow-lg">
            <IconPlus width={13} height={13} />
            Clique no mapa para adicionar um ponto
          </div>
        )}
        {ready && !onMapClick && (
          <div className="pointer-events-none absolute right-3 top-3 z-[500] rounded-full border border-base-300 bg-base-100/85 px-2.5 py-1 text-[10px] font-medium text-base-content/60 backdrop-blur-sm">
            Explore o mapa — ative &quot;Adicionar ponto&quot; para inserir
          </div>
        )}
      </div>
    </div>
  );
}

export { routeColor };