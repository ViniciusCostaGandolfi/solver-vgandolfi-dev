import { POLL_DELAYS_MS, POLL_FINAL_INTERVAL_MS } from "../lib/hooks/useRoutingJob";
import type { JobResponse, JobStatusResponse } from "../lib/types";
import {
  copyToClipboard,
  fmtDateTime,
  fmtDurationMs,
} from "../lib/format";
import { TYPE_LABEL } from "../lib/labels";
import {
  IconAlert,
  IconRefresh,
  IconRoute,
  IconTerminal,
} from "./icons";
import { InfoRow, StatusBadge } from "./ui";

interface JobStatusCardProps {
  job: JobResponse;
  status: JobStatusResponse | null;
  polls: number;
  curlForJob: string;
  pollingPaused: boolean;
  onShowToast: (msg: string, kind: "success" | "error" | "info") => void;
}

export function JobStatusCard({
  job,
  status,
  polls,
  curlForJob,
  pollingPaused,
  onShowToast,
}: JobStatusCardProps) {
  return (
    <div className="card card-border mt-6 scroll-mt-24">
      <div className="card-body gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center rounded-box bg-primary/10 text-primary">
              <IconRoute width={18} height={18} />
            </span>
            <div>
              <p className="font-display text-sm font-semibold">
                Job de otimização
              </p>
              <p className="font-mono text-xs text-base-content/50">{job.id}</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge badge-ghost badge-sm font-mono">
              {TYPE_LABEL[status?.type ?? job.type]}
            </span>
            {status && <StatusBadge status={status.status} />}
            {curlForJob && (
              <button
                type="button"
                className="btn btn-outline btn-sm gap-1.5"
                onClick={() => {
                  void copyToClipboard(curlForJob).then((ok) => {
                    if (ok) onShowToast("Comando curl copiado.", "success");
                  });
                }}
              >
                <IconTerminal width={15} height={15} />
                Copiar curl
              </button>
            )}
          </div>
        </div>

        {/* Passos de progresso */}
        <ul className="steps steps-vertical sm:steps-horizontal">
          <li
            className={`step ${
              status && status.status !== "PENDING" ? "step-primary" : ""
            }`}
          >
            Enviado
          </li>
          <li
            className={`step ${
              status &&
              (status.status === "RUNNING" ||
                status.status === "DONE" ||
                status.status === "ERROR")
                ? "step-primary"
                : ""
            }`}
          >
            Processando
          </li>
          <li
            className={`step ${
              status?.status === "DONE"
                ? "step-success"
                : status?.status === "ERROR"
                  ? "step-error"
                  : ""
            }`}
          >
            {status?.status === "ERROR" ? "Falhou" : "Concluído"}
          </li>
        </ul>

        {/* Detalhes */}
        <div className="grid gap-2 sm:grid-cols-2">
          <InfoRow label="ID do job" value={job.id} mono copyText={job.id} />
          <InfoRow
            label="Status URL"
            value={job.statusUrl ?? "—"}
            mono
            copyText={job.statusUrl ?? undefined}
          />
          <InfoRow
            label="Criado em"
            value={fmtDateTime(status?.createdAt ?? job.createdAt)}
          />
          <InfoRow
            label="Iniciado em"
            value={fmtDateTime(status?.startedAt)}
          />
          <InfoRow
            label="Finalizado em"
            value={fmtDateTime(status?.finishedAt)}
          />
          <InfoRow
            label="Webhook"
            value={status?.webhookUrl || "Não configurado"}
            mono={!!status?.webhookUrl}
            copyText={status?.webhookUrl ?? undefined}
          />
          <InfoRow label="Input" value={status?.inputPath ?? "—"} mono />
          <InfoRow label="Output" value={status?.outputPath ?? "—"} mono />
          <InfoRow
            label="Tempo de processamento"
            value={fmtDurationMs(status?.processingTimeMs ?? job.processingTimeMs)}
          />
          <InfoRow label="Consultas de status" value={`${polls}`} />
          <InfoRow
            label="Intervalo de verificação"
            value={`progressivo (${POLL_DELAYS_MS.map((d) => `${d / 1000}s`).join(", ")}, depois ${POLL_FINAL_INTERVAL_MS / 1000}s)`}
          />
        </div>

        {(status?.status === "PENDING" || status?.status === "RUNNING") && (
          <div className="flex items-center gap-2 rounded-field border border-base-200 bg-base-100 px-3 py-2 text-xs text-base-content/60">
            <span className="loading loading-spinner loading-xs text-primary" />
            Aguardando o solver processar…
          </div>
        )}

        {status?.status === "ERROR" && (
          <div role="alert" className="alert alert-error alert-soft">
            <IconAlert width={18} height={18} />
            <div>
              <p className="text-sm font-semibold">Não foi possível resolver</p>
              <p className="text-xs opacity-80">
                {status.errorMessage || "Erro interno do solver."}
              </p>
            </div>
          </div>
        )}

        {pollingPaused && (
          <div role="alert" className="alert alert-warning alert-soft">
            <IconRefresh width={18} height={18} />
            <span>
              Rate limit atingido. A verificação de status será retomada
              automaticamente em 60 segundos.
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
