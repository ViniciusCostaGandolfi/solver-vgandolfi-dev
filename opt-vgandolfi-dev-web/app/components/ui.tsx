import type { JobStatusValue } from "../lib/types";
import { CopyButton } from "./CopyButton";

/** Cabeçalho de seção: eyebrow mono + título display + descrição opcional. */
export function SectionHeading({
  eyebrow,
  title,
  desc,
}: {
  eyebrow: string;
  title: string;
  desc?: string;
}) {
  return (
    <div className="mb-6">
      <p className="mb-1.5 font-mono text-xs font-medium uppercase tracking-widest text-base-content/40">
        {eyebrow}
      </p>
      <h2 className="font-display text-2xl font-bold tracking-tight sm:text-3xl">
        {title}
      </h2>
      {desc && <p className="mt-2 max-w-2xl text-sm text-base-content/60">{desc}</p>}
    </div>
  );
}

const STATUS_CFG: Record<
  JobStatusValue,
  { badge: string; dot: string; label: string }
> = {
  PENDING: { badge: "badge-info badge-soft", dot: "status-info", label: "Na fila" },
  RUNNING: { badge: "badge-warning badge-soft", dot: "status-warning", label: "Processando" },
  DONE: { badge: "badge-success badge-soft", dot: "status-success", label: "Concluído" },
  ERROR: { badge: "badge-error badge-soft", dot: "status-error", label: "Erro" },
};

export function StatusBadge({ status }: { status: JobStatusValue }) {
  const cfg = STATUS_CFG[status];
  return (
    <span className={`badge gap-1.5 ${cfg.badge}`}>
      <span className={`status ${cfg.dot} status-xs`} />
      {cfg.label}
    </span>
  );
}

export function InfoRow({
  label,
  value,
  mono = false,
  copyText,
}: {
  label: string;
  value: string;
  mono?: boolean;
  copyText?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-2 rounded-field border border-base-200 bg-base-100 px-3 py-2">
      <span className="shrink-0 text-xs text-base-content/50">{label}</span>
      <span
        className={`truncate text-sm ${mono ? "font-mono text-xs" : ""}`}
        title={value}
      >
        {value}
      </span>
      {copyText && (
        <CopyButton text={copyText} className="btn-xs h-7 min-h-0 shrink-0 px-1.5" />
      )}
    </div>
  );
}
