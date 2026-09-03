import { MATRIX_LABEL, TYPE_LABEL } from "../lib/labels";
import type { OriginState } from "../lib/payload";
import { isValidLatLng, parseCoord } from "../lib/payload";
import type { MatrixType, PointRow, ProblemType, VehicleRow } from "../lib/types";
import { IconAlert, IconZap } from "./icons";

interface SubmitBarProps {
  problemType: ProblemType;
  matrixType: MatrixType;
  points: PointRow[];
  vehicles: VehicleRow[];
  origin: OriginState;
  webhookUrl: string;
  submitting: boolean;
  validationError: string | null;
  onWebhookChange: (url: string) => void;
  onOptimize: () => void;
}

export function SubmitBar({
  problemType,
  matrixType,
  points,
  vehicles,
  origin,
  webhookUrl,
  submitting,
  validationError,
  onWebhookChange,
  onOptimize,
}: SubmitBarProps) {
  return (
    <div className="card card-border mt-6 bg-base-200/50">
      <div className="card-body">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div className="w-full space-y-3 md:max-w-md">
            <div>
              <p className="text-sm font-semibold">{TYPE_LABEL[problemType]}</p>
              <p className="mt-0.5 text-xs text-base-content/60">
                {MATRIX_LABEL[matrixType]} · {points.length}{" "}
                {points.length === 1 ? "ponto" : "pontos"}
                {problemType === "VRP" &&
                  ` · ${vehicles.length} ${vehicles.length === 1 ? "veículo" : "veículos"}`}
                {problemType !== "DISTANCE_MATRIX" &&
                  (isValidLatLng(parseCoord(origin.lat), parseCoord(origin.lng))
                    ? " · origem definida"
                    : " · origem pendente")}
              </p>
            </div>
            <label className="block">
              <span className="mb-1 block text-xs font-medium text-base-content/50">
                Webhook (opcional) — receba o resultado via POST quando concluir
              </span>
              <input
                type="url"
                className="input  input-sm w-full font-mono"
                value={webhookUrl}
                placeholder="https://seu-servidor.com/hook"
                onChange={(e) => onWebhookChange(e.target.value)}
              />
            </label>
          </div>
          <button
            type="button"
            className="btn btn-primary btn-lg w-full md:w-auto"
            onClick={onOptimize}
            disabled={submitting}
          >
            {submitting ? (
              <>
                <span className="loading loading-spinner loading-sm" />
                Enviando…
              </>
            ) : (
              <>
                <IconZap width={19} height={19} />
                Otimizar
              </>
            )}
          </button>
        </div>
        {validationError && (
          <div role="alert" className="alert alert-error alert-soft mt-3">
            <IconAlert width={18} height={18} />
            <span>{validationError}</span>
          </div>
        )}
      </div>
    </div>
  );
}
