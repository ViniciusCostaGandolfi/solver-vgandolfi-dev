import { useEffect, useMemo, useRef, useState } from "react";
import type { MapRoute } from "../../components/MapCanvas";
import { routeColor } from "../../components/MapCanvas";
import {
  ApiError,
  createJob,
  getJobOutput,
  getJobStatus,
  jobTypeRoute,
} from "../api";
import { apiAbsoluteUrl } from "../format";
import {
  isMatrixOutput,
  isTspOutput,
  isVrpOutput,
  routeDtoCoords,
  tspRouteCoords,
  vrpRoutes as extractVrpRoutes,
} from "../output";
import type { OriginState } from "../payload";
import {
  buildMatrixInput,
  buildTspInput,
  buildVrpInput,
  validateProblem,
} from "../payload";
import type {
  JobResponse,
  JobStatusResponse,
  MatrixType,
  PointRow,
  ProblemType,
  RouteDto,
  SolverOutput,
  VehicleRow,
} from "../types";
import type { ToastKind } from "./useToast";

/** Intervalos progressivos entre polls: 1s, 2s, 3s, 5s e depois 10s fixo. */
export const POLL_DELAYS_MS = [1000, 2000, 3000, 5000];
export const POLL_FINAL_INTERVAL_MS = 10000;
const RATE_LIMIT_PAUSE_MS = 60000;
/** Backoff entre tentativas de baixar o output de um job DONE (evita hot loop). */
const OUTPUT_RETRY_MS = 8000;
/** Máximo de tentativas extras após erro transitório antes de desistir. */
const OUTPUT_MAX_RETRIES = 3;

/** Erros considerados transitórios para retry do output (409/429/5xx/rede).
 *  Erros permanentes (4xx exceto 409) não melhoram com nova tentativa. */
function isTransientError(err: unknown): boolean {
  if (err instanceof TypeError) return true; // falha de rede
  if (err instanceof ApiError) {
    return err.status === 409 || err.status === 429 || err.status >= 500;
  }
  return false;
}

export interface UseRoutingJob {
  job: JobResponse | null;
  status: JobStatusResponse | null;
  output: SolverOutput | null;
  lastInput: string;
  submitting: boolean;
  polls: number;
  pollingPaused: boolean;
  validationError: string | null;
  terminal: boolean;
  curlForJob: string;
  mapRoutes: MapRoute[];
  vrpRouteList: RouteDto[];
  matrix: number[][];
  handleOptimize: () => Promise<void>;
  reset: () => void;
}

interface UseRoutingJobArgs {
  problemType: ProblemType;
  matrixType: MatrixType;
  origin: OriginState;
  points: PointRow[];
  vehicles: VehicleRow[];
  webhookUrl: string;
  showToast: (msg: string, kind: ToastKind) => void;
  statusRef: React.RefObject<HTMLDivElement | null>;
}

/** Submissão, polling, output e estado do job de otimização. */
export function useRoutingJob({
  problemType,
  matrixType,
  origin,
  points,
  vehicles,
  webhookUrl,
  showToast,
  statusRef,
}: UseRoutingJobArgs): UseRoutingJob {
  const [job, setJob] = useState<JobResponse | null>(null);
  const [status, setStatus] = useState<JobStatusResponse | null>(null);
  const [output, setOutput] = useState<SolverOutput | null>(null);
  const [lastInput, setLastInput] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [polls, setPolls] = useState(0);
  const [pollingPaused, setPollingPaused] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  /* showToast do useToast é recriada a cada render (função não memoizada).
     Guardamos numa ref estável para não depender da identidade dela nos
     efeitos — caso contrário, qualquer re-render religaria os efeitos. */
  const showToastRef = useRef(showToast);
  useEffect(() => {
    showToastRef.current = showToast;
  }, [showToast]);

  /* Guarda de idempotência do fetch de output: registra o jobId cujo output já
     foi buscado com sucesso ou está em andamento. Sem ela, cada re-render com
     nova referência de showToast dispararia outro GET /output em loop. */
  const outputJobRef = useRef<string | null>(null);
  const outputRetryTimerRef = useRef<number | null>(null);

  const clearOutputFetchState = () => {
    outputJobRef.current = null;
    if (outputRetryTimerRef.current !== null) {
      window.clearTimeout(outputRetryTimerRef.current);
      outputRetryTimerRef.current = null;
    }
  };

  const reset = () => {
    setJob(null);
    setStatus(null);
    setOutput(null);
    setValidationError(null);
    setPolls(0);
    setPollingPaused(false);
    clearOutputFetchState();
  };

  /* ---------------------- submissão ---------------------- */
  const buildInput = (type: ProblemType): Record<string, unknown> => {
    if (type === "TSP") return buildTspInput(origin, points, matrixType);
    if (type === "VRP") return buildVrpInput(origin, points, vehicles, matrixType);
    return buildMatrixInput(points, matrixType);
  };

  const handleOptimize = async () => {
    const err = validateProblem(problemType, origin, points, vehicles, webhookUrl);
    if (err) {
      setValidationError(err);
      showToast(err, "error");
      return;
    }
    setValidationError(null);
    setSubmitting(true);
    try {
      const input = buildInput(problemType);
      const webhook = webhookUrl.trim() || undefined;
      setLastInput(JSON.stringify({ webhookUrl: webhook, input }));
      const created = await createJob(problemType, input, webhook);
      setJob(created);
      setStatus({
        id: created.id,
        type: created.type,
        status: created.status,
        outputUrl: created.outputUrl ?? null,
        errorMessage: null,
        processingTimeMs: created.processingTimeMs ?? null,
        createdAt: created.createdAt ?? "",
      });
      setOutput(null);
      clearOutputFetchState();
      setPolls(0);
      setPollingPaused(false);
      window.setTimeout(() => {
        statusRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 80);
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : "Falha ao criar o job de otimização.";
      showToast(msg, "error");
    } finally {
      setSubmitting(false);
    }
  };

  /* ---------------------- polling ---------------------- */
  const jobId = job?.id;
  const terminal = status?.status === "DONE" || status?.status === "ERROR";

  useEffect(() => {
    if (!jobId || terminal || pollingPaused) return;
    let stopped = false;
    let tickCount = 0;

    const tick = async () => {
      try {
        const s = await getJobStatus(jobId);
        if (stopped) return;
        setStatus(s);
        setPolls((n) => n + 1);
      } catch (err) {
        if (stopped) return;
        if (err instanceof ApiError && err.status === 429) {
          setPollingPaused(true);
          showToastRef.current(
            "Limite de consultas atingido. A verificação é retomada automaticamente em 60 s.",
            "info",
          );
          window.setTimeout(() => setPollingPaused(false), RATE_LIMIT_PAUSE_MS);
        } else {
          const msg =
            err instanceof ApiError
              ? err.message
              : "Falha ao consultar o status do job.";
          showToastRef.current(msg, "error");
        }
      }
    };

    const schedule = () => {
      if (stopped) return;
      // Escada progressiva: 1s, 2s, 3s, 5s e depois 10s fixo.
      const delay =
        tickCount < POLL_DELAYS_MS.length
          ? POLL_DELAYS_MS[tickCount]
          : POLL_FINAL_INTERVAL_MS;
      tickCount += 1;
      window.setTimeout(() => {
        void tick().then(schedule);
      }, delay);
    };

    void tick().then(schedule);

    return () => {
      stopped = true;
    };
  }, [jobId, terminal, pollingPaused]);

  /* Busca o output assim que o job fica DONE (efeito separado para não ser
     cancelado pelo teardown do polling quando o status transiciona).

     Corrige o hot loop de /output: o efeito depende apenas de [jobId,
     status?.status] — showToast fica numa ref, pois o useToast recria a função
     a cada render. A guarda outputJobRef garante idempotência por jobId: o
     output é buscado uma única vez por job; em erro transitório (409/429/rede)
     a guarda é liberada e a nova tentativa só ocorre após OUTPUT_RETRY_MS
     (nunca em loop apertado), limitada a OUTPUT_MAX_RETRIES. Erros permanentes
     (4xx exceto 409) são exibidos uma vez, sem retry. */
  useEffect(() => {
    if (!jobId || status?.status !== "DONE") return;
    if (outputJobRef.current === jobId) return; // já buscou ou está em andamento
    outputJobRef.current = jobId; // marca como em andamento (idempotência)
    let cancelled = false;
    let retries = 0;

    const fetchOutput = async () => {
      outputJobRef.current = jobId;
      try {
        const out = await getJobOutput<SolverOutput>(jobId);
        if (cancelled || outputJobRef.current !== jobId) return;
        setOutput(out); // sucesso: guarda continua marcada → não busca de novo
      } catch (err) {
        if (cancelled || outputJobRef.current !== jobId) return;
        const msg =
          err instanceof ApiError
            ? err.message
            : "Falha ao baixar o resultado.";
        // Erro permanente (4xx exceto 409) ou tentativas esgotadas: expõe o
        // erro uma única vez e mantém a guarda marcada (sem refetch/re-retry).
        if (!isTransientError(err) || retries >= OUTPUT_MAX_RETRIES) {
          showToastRef.current(msg, "error");
          return;
        }
        // Erro transitório (409/429/rede): libera a guarda e agenda uma nova
        // tentativa com backoff — nunca loop síncrono/tight, e limitada a
        // OUTPUT_MAX_RETRIES para não martelar /output por tempo indefinido.
        retries += 1;
        outputJobRef.current = null;
        showToastRef.current(msg, "error");
        if (outputRetryTimerRef.current !== null) {
          window.clearTimeout(outputRetryTimerRef.current);
        }
        outputRetryTimerRef.current = window.setTimeout(() => {
          outputRetryTimerRef.current = null;
          if (!cancelled) void fetchOutput();
        }, OUTPUT_RETRY_MS);
      }
    };

    void fetchOutput();

    return () => {
      cancelled = true;
      if (outputRetryTimerRef.current !== null) {
        window.clearTimeout(outputRetryTimerRef.current);
        outputRetryTimerRef.current = null;
      }
    };
  }, [jobId, status?.status]);

  /* ---------------------- curl do job ---------------------- */
  const curlForJob = useMemo(() => {
    if (!job || !lastInput) return "";
    const escaped = lastInput.replace(/'/g, "'\\''");
    const url = apiAbsoluteUrl(`/jobs/${jobTypeRoute(problemType)}`);
    return [
      `curl -X POST ${url}`,
      `  -H 'Content-Type: application/json'`,
      `  -d '${escaped}'`,
    ].join("\n");
  }, [job, lastInput, problemType]);

  /* ---------------------- rotas para o mapa ---------------------- */
  const mapRoutes: MapRoute[] = useMemo(() => {
    if (!output) return [];
    if (problemType === "TSP" && isTspOutput(output)) {
      const coords = tspRouteCoords(output);
      if (coords.length >= 2) {
        return [{ id: "tsp-route", coords, color: "#6366f1" }];
      }
      return [];
    }
    if (problemType === "VRP" && isVrpOutput(output)) {
      return extractVrpRoutes(output)
        .map((r, i) => ({
          id: `vrp-route-${i}`,
          coords: routeDtoCoords(r),
          color: routeColor(i),
        }))
        .filter((r) => r.coords.length >= 2);
    }
    return [];
  }, [output, problemType]);

  /* ---------------------- valores derivados do resultado ---------------------- */
  const vrpRouteList: RouteDto[] = useMemo(
    () => (isVrpOutput(output) ? extractVrpRoutes(output) : []),
    [output],
  );

  const matrix: number[][] = useMemo(() => {
    if (!isMatrixOutput(output)) return [];
    return output.matrix ?? [];
  }, [output]);

  return {
    job,
    status,
    output,
    lastInput,
    submitting,
    polls,
    pollingPaused,
    validationError,
    terminal,
    curlForJob,
    mapRoutes,
    vrpRouteList,
    matrix,
    handleOptimize,
    reset,
  };
}
