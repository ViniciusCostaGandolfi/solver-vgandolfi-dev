import type { MapPoint, MapRoute } from "./MapCanvas";
import { MapCanvas, routeColor } from "./MapCanvas";
import { IconHome, IconInfo, IconPlus } from "./icons";

interface MapPanelProps {
  points: MapPoint[];
  routes: MapRoute[];
  dark: boolean;
  problemType: "TSP" | "VRP" | "DISTANCE_MATRIX";
  onPointDrag: (id: string, lat: number, lng: number) => void;
  onMapClick: (lat: number, lng: number) => void;
  containerRef?: React.RefObject<HTMLDivElement | null>;
  /** Modo explícito: quando ativo, um clique no mapa adiciona um ponto. */
  addPointMode: boolean;
  onToggleAddPointMode: () => void;
}

export function MapPanel({
  points,
  routes,
  dark,
  problemType,
  onPointDrag,
  onMapClick,
  containerRef,
  addPointMode,
  onToggleAddPointMode,
}: MapPanelProps) {
  return (
    <div
      ref={containerRef}
      className="card card-border scroll-mt-24 lg:sticky lg:top-24"
    >
      <div className="card-body gap-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="flex items-center gap-2 font-display font-semibold">
            <span className="grid h-8 w-8 place-items-center rounded-box bg-success/15 text-success">
              <IconHome width={16} height={16} />
            </span>
            Mapa
          </h3>
          <div className="flex items-center gap-2">
            <span className="badge badge-ghost badge-sm font-mono">
              {points.length} pts
            </span>
            <button
              type="button"
              onClick={onToggleAddPointMode}
              aria-pressed={addPointMode}
              className={`btn btn-sm gap-1.5 ${
                addPointMode ? "btn-primary" : "btn-outline"
              }`}
            >
              <IconPlus width={15} height={15} />
              Adicionar ponto no mapa
            </button>
          </div>
        </div>

        <MapCanvas
          points={points}
          routes={routes}
          dark={dark}
          onPointChange={onPointDrag}
          onMapClick={addPointMode ? onMapClick : undefined}
          minHeight={440}
          
        />

        {problemType === "VRP" && routes.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {routes.map((r, i) => (
              <span
                key={r.id}
                className="flex items-center gap-1.5 rounded-full border border-base-200 px-2.5 py-1 text-[11px] text-base-content/70"
              >
                <span
                  className="h-2 w-2 rounded-full"
                  style={{ backgroundColor: routeColor(i) }}
                />
                Rota {i + 1}
              </span>
            ))}
          </div>
        )}

        <p className="flex items-center gap-1.5 text-xs text-base-content/50">
          <IconInfo width={13} height={13} />
          Arraste os marcadores para ajustar. Para inserir pontos, ative
          &quot;Adicionar ponto no mapa&quot; e clique onde quiser.
        </p>
      </div>
    </div>
  );
}
