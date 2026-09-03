import type {
  RouteDto,
  SolverOutput,
  TspOutput,
  VrpOutput,
} from "../lib/types";
import {
  fmtDistanceMeters,
  fmtDurationMs,
  fmtNumber,
  downloadJson,
} from "../lib/format";
import { TYPE_LABEL } from "../lib/labels";
import { isTspOutput, isVrpOutput, routeDtoCoords, tspRouteCoords, vrpRoutes } from "../lib/output";
import type { ProblemType } from "../lib/types";
import { routeColor } from "./MapCanvas";
import {
  IconArrowRight,
  IconCheck,
  IconClock,
  IconDownload,
  IconGrid,
  IconLayers,
  IconMapPin,
  IconRoute,
  IconTruck,
} from "./icons";

interface ResultPanelsProps {
  problemType: ProblemType;
  output: SolverOutput;
  matrix: number[][];
  jobId: string | undefined;
  processingTimeMs: number | undefined;
  onViewOnMap?: () => void;
}

export function ResultPanels({
  problemType,
  output,
  matrix,
  jobId,
  processingTimeMs,
  onViewOnMap,
}: ResultPanelsProps) {
  const canViewOnMap = Boolean(
    onViewOnMap &&
      ((problemType === "TSP" &&
        isTspOutput(output) &&
        tspRouteCoords(output).length >= 2) ||
        (problemType === "VRP" &&
          isVrpOutput(output) &&
          vrpRoutes(output).some((r) => routeDtoCoords(r).length >= 2))),
  );

  return (
    <div className="card card-border mt-6">
      <div className="card-body gap-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center rounded-box bg-success/15 text-success">
              <IconCheck width={18} height={18} />
            </span>
            <div>
              <h3 className="font-display text-lg font-bold">
                Resultado otimizado
              </h3>
              <p className="text-xs text-base-content/50">
                {TYPE_LABEL[problemType]} ·{" "}
                {fmtDurationMs(
                  (output as { time_to_solve_ms?: number }).time_to_solve_ms ??
                    processingTimeMs,
                )}{" "}
                para resolver
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canViewOnMap && (
              <button
                type="button"
                className="btn btn-outline btn-sm gap-1.5"
                onClick={onViewOnMap}
              >
                <IconMapPin width={15} height={15} />
                Ver no mapa
              </button>
            )}
            <button
              type="button"
              className="btn btn-outline btn-sm gap-1.5"
              onClick={() =>
                downloadJson(output, `solver-resultado-${jobId ?? "job"}.json`)
              }
            >
              <IconDownload width={15} height={15} />
              Baixar JSON
            </button>
          </div>
        </div>

        {problemType === "TSP" && isTspOutput(output) && (
          <TspResult output={output} />
        )}

        {problemType === "VRP" && isVrpOutput(output) && (
          <VrpResult output={output} />
        )}

        {problemType === "DISTANCE_MATRIX" && (
          <MatrixResult matrix={matrix} />
        )}
      </div>
    </div>
  );
}

/* ------------------------------ TspResult ------------------------------ */

function TspResult({ output }: { output: TspOutput }) {
  const distance = output.distance_meters ?? 0;
  const stops = output.optimized_stops ?? [];
  const stopsCount = stops.length;
  const ordered = stops.map(
    (s) =>
      s.customer_name ?? s.address?.latitude?.toString() ?? "Ponto",
  );

  return (
    <div className="space-y-5">
      <div className="stats stats-vertical stats-sm-horizontal w-full shadow-sm">
        <div className="stat">
          <div className="stat-figure text-primary">
            <IconRoute width={22} height={22} />
          </div>
          <div className="stat-title">Distância total</div>
          <div className="stat-value text-2xl">{fmtDistanceMeters(distance)}</div>
          <div className="stat-desc">rota otimizada</div>
        </div>
        <div className="stat">
          <div className="stat-figure text-secondary">
            <IconMapPin width={22} height={22} />
          </div>
          <div className="stat-title">Paradas</div>
          <div className="stat-value text-2xl">{stopsCount}</div>
          <div className="stat-desc">mais a origem</div>
        </div>
        <div className="stat">
          <div className="stat-figure text-accent">
            <IconClock width={22} height={22} />
          </div>
          <div className="stat-title">Tempo de solução</div>
          <div className="stat-value text-2xl">
            {fmtDurationMs(output.time_to_solve_ms)}
          </div>
          <div className="stat-desc">processamento</div>
        </div>
      </div>

      {ordered.length > 0 && (
        <div className="rounded-field border border-base-200 bg-base-100 p-4">
          <p className="mb-3 text-xs font-medium uppercase tracking-wider text-base-content/50">
            Ordem de visita
          </p>
          <ol className="flex flex-wrap items-center gap-1.5">
            <li className="badge badge-accent badge-soft badge-sm">origem</li>
            {ordered.map((name, i) => (
              <li key={`${name}-${i}`} className="flex items-center gap-1.5">
                <IconArrowRight
                  width={13}
                  height={13}
                  className="text-base-content/30"
                />
                <span className="badge badge-outline badge-sm font-normal">
                  {i + 1}. {name}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  );
}

/* ------------------------------ VrpResult ------------------------------ */

function VrpResult({ output }: { output: VrpOutput }) {
  const routes = vrpRoutes(output);
  const totalDistance = routes.reduce((a, r) => a + (r.distance_meters ?? 0), 0);
  const totalStops = routes.reduce(
    (a, r) => a + (r.route_deliveries ?? r.clients?.length ?? 0),
    0,
  );
  const totalVolume = routes.reduce((a, r) => a + (r.volume_liters ?? 0), 0);
  const totalWeight = routes.reduce((a, r) => a + (r.weight_kg ?? 0), 0);

  return (
    <div className="space-y-5">
      <div className="stats stats-vertical stats-sm-horizontal w-full shadow-sm">
        <div className="stat">
          <div className="stat-figure text-primary">
            <IconTruck width={22} height={22} />
          </div>
          <div className="stat-title">Rotas</div>
          <div className="stat-value text-2xl">{routes.length}</div>
          <div className="stat-desc">veículos usados</div>
        </div>
        <div className="stat">
          <div className="stat-figure text-secondary">
            <IconMapPin width={22} height={22} />
          </div>
          <div className="stat-title">Paradas</div>
          <div className="stat-value text-2xl">{totalStops}</div>
          <div className="stat-desc">entregas no total</div>
        </div>
        <div className="stat">
          <div className="stat-figure text-accent">
            <IconRoute width={22} height={22} />
          </div>
          <div className="stat-title">Distância total</div>
          <div className="stat-value text-2xl">{fmtDistanceMeters(totalDistance)}</div>
          <div className="stat-desc">soma das rotas</div>
        </div>
      </div>

      {routes.length === 0 ? (
        <p className="rounded-field border border-dashed border-base-300 p-5 text-center text-sm text-base-content/50">
          Nenhuma rota foi gerada — provavelmente a demanda excede a capacidade
          dos veículos.
        </p>
      ) : (
        <div className="space-y-3">
          {routes.map((r, i) => (
            <RouteRow
              key={r.vehicle_id ?? `route-${i}`}
              route={r}
              index={i}
              timeToSolve={output.time_to_solve_ms}
            />
          ))}
          {totalVolume > 0 && totalWeight > 0 && (
            <p className="text-xs text-base-content/50">
              Carga total: {fmtNumber(totalVolume, 0)} L ·{" "}
              {fmtNumber(totalWeight, 0)} kg
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function RouteRow({
  route: r,
  index: i,
  timeToSolve,
}: {
  route: RouteDto;
  index: number;
  timeToSolve?: number;
}) {
  return (
    <div
      className="grid gap-3 rounded-field border border-base-200 bg-base-100 p-4 sm:grid-cols-[1.5fr,repeat(4,1fr)] sm:items-center"
    >
      <div className="flex items-center gap-2.5">
        <span
          className="h-3 w-3 shrink-0 rounded-full"
          style={{ backgroundColor: routeColor(i) }}
        />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">
            {r.vehicle_name ?? `Rota ${i + 1}`}
          </p>
          <p className="text-xs text-base-content/50">
            {r.route_deliveries ?? r.clients?.length ?? 0} paradas
          </p>
        </div>
      </div>
      <div>
        <p className="text-xs text-base-content/50">Distância</p>
        <p className="font-mono text-sm">{fmtDistanceMeters(r.distance_meters)}</p>
      </div>
      <div>
        <p className="text-xs text-base-content/50">Volume</p>
        <p className="font-mono text-sm">{fmtNumber(r.volume_liters ?? 0, 0)} L</p>
      </div>
      <div>
        <p className="text-xs text-base-content/50">Peso</p>
        <p className="font-mono text-sm">{fmtNumber(r.weight_kg ?? 0, 0)} kg</p>
      </div>
      <div className="hidden sm:block">
        <p className="text-xs text-base-content/50">Tempo de solução</p>
        <p className="font-mono text-sm">{fmtDurationMs(timeToSolve)}</p>
      </div>
    </div>
  );
}

/* ---------------------------- MatrixResult ---------------------------- */

function MatrixResult({ matrix }: { matrix: number[][] }) {
  const flat = matrix.flat().filter((v) => Number.isFinite(v));
  const min = flat.length ? Math.min(...flat) : 0;
  const max = flat.length ? Math.max(...flat) : 0;
  const n = matrix.length;

  return (
    <div className="space-y-5">
      <div className="stats stats-vertical stats-sm-horizontal w-full shadow-sm">
        <div className="stat">
          <div className="stat-figure text-primary">
            <IconGrid width={22} height={22} />
          </div>
          <div className="stat-title">Dimensão</div>
          <div className="stat-value text-2xl">
            {n}×{matrix[0]?.length ?? 0}
          </div>
          <div className="stat-desc">pares de pontos</div>
        </div>
        <div className="stat">
          <div className="stat-figure text-secondary">
            <IconRoute width={22} height={22} />
          </div>
          <div className="stat-title">Menor distância</div>
          <div className="stat-value text-2xl">{fmtDistanceMeters(min)}</div>
          <div className="stat-desc">entre dois pontos</div>
        </div>
        <div className="stat">
          <div className="stat-figure text-accent">
            <IconLayers width={22} height={22} />
          </div>
          <div className="stat-title">Maior distância</div>
          <div className="stat-value text-2xl">{fmtDistanceMeters(max)}</div>
          <div className="stat-desc">pior par</div>
        </div>
      </div>

      <div className="overflow-x-auto rounded-field border border-base-200 bg-base-100">
        <table className="table table-xs">
          <thead>
            <tr>
              <th className="sticky left-0 z-10 bg-base-100 font-mono">de ↓ para</th>
              {matrix[0]?.map((_, j) => (
                <th key={j} className="text-center font-mono">
                  P{j + 1}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {matrix.map((row, i) => (
              <tr key={i}>
                <th className="sticky left-0 z-10 bg-base-100 font-mono">
                  P{i + 1}
                </th>
                {row.map((cell, j) => (
                  <td
                    key={j}
                    className={`text-center font-mono text-xs ${
                      i === j ? "text-base-content/30" : ""
                    }`}
                  >
                    {i === j ? "·" : fmtDistanceMeters(cell)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-base-content/50">
        Valores em metros na diagonal zero. Use o botão acima para baixar o JSON
        completo.
      </p>
    </div>
  );
}
