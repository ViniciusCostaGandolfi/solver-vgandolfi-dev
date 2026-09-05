import type { ReactNode, RefObject } from "react";
import type { OriginState } from "../lib/payload";
import type { MatrixType, PointRow, ProblemType, VehicleRow } from "../lib/types";
import { AddressSearch } from "./AddressSearch";
import {
  IconCheck,
  IconFlag,
  IconMapPin,
  IconNavigation,
  IconPlus,
  IconTrash,
  IconTruck,
  IconUpload,
  IconX,
} from "./icons";

interface OptimizerFormProps {
  problemType: ProblemType;
  matrixType: MatrixType;
  origin: OriginState;
  points: PointRow[];
  vehicles: VehicleRow[];
  geoBusy: boolean;
  fileInputRef: RefObject<HTMLInputElement | null>;
  mapPanel: ReactNode;
  onMatrixTypeChange: (type: MatrixType) => void;
  onOriginChange: (patch: Partial<OriginState>) => void;
  onAddPoint: (data?: Partial<PointRow>) => void;
  onUpdatePoint: (id: string, patch: Partial<PointRow>) => void;
  onRemovePoint: (id: string) => void;
  onAddVehicle: () => void;
  onUpdateVehicle: (id: string, patch: Partial<VehicleRow>) => void;
  onRemoveVehicle: (id: string) => void;
  onClearPoints: () => void;
  onOriginGeocode: (r: {
    formattedAddress: string;
    latitude: number;
    longitude: number;
  }) => void;
  onAddByAddress: (r: {
    formattedAddress: string;
    latitude: number;
    longitude: number;
  }) => void;
  onUseMyLocation: () => void;
  onFile: (file: File | undefined) => void;
}

export function OptimizerForm(props: OptimizerFormProps) {
  const { mapPanel } = props;
  return (
    <>
      <ConfigBar {...props} />
      <div className="mt-6 grid items-start gap-6 lg:grid-cols-2">
        <div className="min-w-0 space-y-6">
          {props.problemType !== "DISTANCE_MATRIX" && (
            <OriginEditor {...props} />
          )}
          {props.problemType === "VRP" && <VehiclesEditor {...props} />}
          <PointsTable {...props} />
        </div>
        {mapPanel}
      </div>
    </>
  );
}

/* ------------------------------ ConfigBar ------------------------------ */

function ConfigBar({
  matrixType,
  onMatrixTypeChange,
  onAddByAddress,
  fileInputRef,
  onFile,
  onClearPoints,
}: OptimizerFormProps) {
  return (
    <div className="mt-6 flex flex-col gap-3 lg:flex-row lg:items-end">
      <div className="w-full lg:w-56">
        <label className="mb-1 block text-xs font-medium text-base-content/60">
          Tipo de matriz
        </label>
        <select
          className="select select-sm w-full"
          value={matrixType}
          onChange={(e) => onMatrixTypeChange(e.target.value as MatrixType)}
        >
          <option value="EUCLIDIAN">Euclidiana (linha reta)</option>
          <option value="STREET">Rodoviária (OSRM)</option>
        </select>
        {matrixType === "STREET" && (
          <p className="mt-1 text-[11px] leading-tight text-base-content/50">
            Rotas pelas vias disponíveis apenas para a região Sudeste do Brasil.
          </p>
        )}
      </div>
      <div className="w-full flex-1">
        <label className="mb-1 block text-xs font-medium text-base-content/60">
          Adicionar ponto por endereço
        </label>
        <AddressSearch
          onSelect={onAddByAddress}
          placeholder="Ex.: Av. Paulista, São Paulo…"
          compact
        />
      </div>
      <div className="flex gap-2">
        <label className="btn btn-outline btn-sm">
          <IconUpload width={16} height={16} />
          Importar CSV/JSON
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,.json,application/json,text/csv"
            className="hidden"
            onChange={(e) => onFile(e.target.files?.[0])}
          />
        </label>
        <button
          type="button"
          className="btn btn-ghost btn-sm text-base-content/60 hover:text-error"
          onClick={onClearPoints}
        >
          <IconX width={16} height={16} />
          Limpar
        </button>
      </div>
    </div>
  );
}

/* ---------------------------- OriginEditor ---------------------------- */

function OriginEditor({
  origin,
  onOriginChange,
  onOriginGeocode,
  onUseMyLocation,
  geoBusy,
}: OptimizerFormProps) {
  return (
    <div className="card card-border">
      <div className="card-body gap-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="flex items-center gap-2 font-display font-semibold">
            <span className="grid h-8 w-8 place-items-center rounded-box bg-accent/15 text-accent">
              <IconFlag width={16} height={16} />
            </span>
            Origem (depósito)
          </h3>
          <button
            type="button"
            className="btn btn-ghost btn-xs gap-1.5"
            onClick={onUseMyLocation}
            disabled={geoBusy}
          >
            {geoBusy ? (
              <span className="loading loading-spinner loading-xs" />
            ) : (
              <IconNavigation width={14} height={14} />
            )}
            Minha localização
          </button>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-base-content/60">
              Latitude
            </span>
            <input
              type="text"
              inputMode="decimal"
              className="input  input-sm w-full font-mono"
              value={origin.lat}
              onChange={(e) => onOriginChange({ lat: e.target.value })}
              placeholder="-23.5505"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-base-content/60">
              Longitude
            </span>
            <input
              type="text"
              inputMode="decimal"
              className="input  input-sm w-full font-mono"
              value={origin.lng}
              onChange={(e) => onOriginChange({ lng: e.target.value })}
              placeholder="-46.6333"
            />
          </label>
        </div>
        <AddressSearch
          onSelect={onOriginGeocode}
          placeholder="Definir origem por endereço…"
          compact
        />
        {origin.name && (
          <p className="flex items-center gap-1.5 text-xs text-base-content/60">
            <IconCheck width={12} height={12} className="text-success" />
            {origin.name}
          </p>
        )}
      </div>
    </div>
  );
}

/* --------------------------- VehiclesEditor --------------------------- */

function VehiclesEditor({
  vehicles,
  onAddVehicle,
  onUpdateVehicle,
  onRemoveVehicle,
}: OptimizerFormProps) {
  return (
    <div className="card card-border">
      <div className="card-body gap-4">
        <div className="flex items-center justify-between">
          <h3 className="flex items-center gap-2 font-display font-semibold">
            <span className="grid h-8 w-8 place-items-center rounded-box bg-primary/10 text-primary">
              <IconTruck width={16} height={16} />
            </span>
            Veículos
          </h3>
          <button
            type="button"
            className="btn btn-ghost btn-xs gap-1"
            onClick={onAddVehicle}
          >
            <IconPlus width={14} height={14} />
            Adicionar veículo
          </button>
        </div>
        <div className="space-y-3">
          {vehicles.map((v, i) => (
            <div
              key={v.id}
              className="grid grid-cols-2 items-end gap-2 rounded-field border border-base-200 p-3 sm:grid-cols-[1.2fr,1fr,1fr,1fr,auto]"
            >
              <label className="block">
                <span className="mb-1 block text-xs font-medium text-base-content/60">
                  Nome
                </span>
                <input
                  type="text"
                  className="input  input-sm w-full"
                  value={v.name}
                  placeholder={`Veículo ${i + 1}`}
                  onChange={(e) => onUpdateVehicle(v.id, { name: e.target.value })}
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-xs font-medium text-base-content/60">
                  Paradas máx.
                </span>
                <input
                  type="text"
                  inputMode="numeric"
                  className="input  input-sm w-full font-mono"
                  value={v.maxDeliveries}
                  onChange={(e) =>
                    onUpdateVehicle(v.id, { maxDeliveries: e.target.value })
                  }
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-xs font-medium text-base-content/60">
                  Peso máx. (kg)
                </span>
                <input
                  type="text"
                  inputMode="numeric"
                  className="input  input-sm w-full font-mono"
                  value={v.maxWeightKg}
                  onChange={(e) =>
                    onUpdateVehicle(v.id, { maxWeightKg: e.target.value })
                  }
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-xs font-medium text-base-content/60">
                  Vol. máx. (L)
                </span>
                <input
                  type="text"
                  inputMode="numeric"
                  className="input  input-sm w-full font-mono"
                  value={v.maxVolumeLiters ?? "0"}
                  onChange={(e) =>
                    onUpdateVehicle(v.id, { maxVolumeLiters: e.target.value })
                  }
                />
              </label>
              <button
                type="button"
                className="btn btn-ghost btn-xs btn-square text-base-content/50 hover:text-error"
                onClick={() => onRemoveVehicle(v.id)}
                aria-label={`Remover ${v.name || `veículo ${i + 1}`}`}
              >
                <IconTrash width={15} height={15} />
              </button>
            </div>
          ))}
          {vehicles.length === 0 && (
            <p className="rounded-field border border-dashed border-base-300 p-3 text-xs text-base-content/50">
              Nenhum veículo. Adicione ao menos um para roteirizar.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

/* ---------------------------- PointsTable ---------------------------- */

function PointsTable({
  problemType,
  points,
  onAddPoint,
  onUpdatePoint,
  onRemovePoint,
  fileInputRef,
  onFile,
}: OptimizerFormProps) {
  return (
    <div className="card card-border">
      <div className="card-body gap-3">
        <div className="flex items-center justify-between">
          <h3 className="flex items-center gap-2 font-display font-semibold">
            <span className="grid h-8 w-8 place-items-center rounded-box bg-secondary/15 text-secondary">
              <IconMapPin width={16} height={16} />
            </span>
            {problemType === "DISTANCE_MATRIX"
              ? "Coordenadas"
              : problemType === "VRP"
                ? "Clientes"
                : "Paradas"}
          </h3>
          <div className="flex items-center gap-2">
            <span className="badge badge-ghost badge-sm">
              {points.length} {points.length === 1 ? "ponto" : "pontos"}
            </span>
            <button
              type="button"
              className="btn btn-ghost btn-xs gap-1"
              onClick={() => onAddPoint()}
            >
              <IconPlus width={14} height={14} />
              Adicionar
            </button>
          </div>
        </div>

        {points.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="table table-sm">
              <thead>
                <tr>
                  <th className="w-8" />
                  {problemType !== "DISTANCE_MATRIX" && <th>Nome</th>}
                  <th>Latitude</th>
                  <th>Longitude</th>
                  {problemType === "VRP" && <th>Vol. (L)</th>}
                  {problemType === "VRP" && <th>Peso (kg)</th>}
                  <th className="w-12" />
                </tr>
              </thead>
              <tbody>
                {points.map((p, i) => (
                  <tr key={p.id}>
                    <td className="font-mono text-xs text-base-content/40">
                      {i + 1}
                    </td>
                    {problemType !== "DISTANCE_MATRIX" && (
                      <td>
                        <input
                          type="text"
                          className="input  input-xs w-full min-w-[130px]"
                          value={p.name}
                          placeholder={`Ponto ${i + 1}`}
                          onChange={(e) =>
                            onUpdatePoint(p.id, { name: e.target.value })
                          }
                        />
                      </td>
                    )}
                    <td>
                      <input
                        type="text"
                        inputMode="decimal"
                        className="input  input-xs w-full min-w-[100px] font-mono"
                        value={p.lat}
                        onChange={(e) =>
                          onUpdatePoint(p.id, { lat: e.target.value })
                        }
                      />
                    </td>
                    <td>
                      <input
                        type="text"
                        inputMode="decimal"
                        className="input  input-xs w-full min-w-[100px] font-mono"
                        value={p.lng}
                        onChange={(e) =>
                          onUpdatePoint(p.id, { lng: e.target.value })
                        }
                      />
                    </td>
                    {problemType === "VRP" && (
                      <td>
                        <input
                          type="text"
                          inputMode="numeric"
                          className="input  input-xs w-full min-w-[80px] font-mono"
                          value={p.volumeLiters ?? "0"}
                          onChange={(e) =>
                            onUpdatePoint(p.id, {
                              volumeLiters: e.target.value,
                            })
                          }
                        />
                      </td>
                    )}
                    {problemType === "VRP" && (
                      <td>
                        <input
                          type="text"
                          inputMode="numeric"
                          className="input  input-xs w-full min-w-[80px] font-mono"
                          value={p.weightKg ?? "0"}
                          onChange={(e) =>
                            onUpdatePoint(p.id, { weightKg: e.target.value })
                          }
                        />
                      </td>
                    )}
                    <td>
                      <button
                        type="button"
                        className="btn btn-ghost btn-xs btn-square text-base-content/50 hover:text-error"
                        onClick={() => onRemovePoint(p.id)}
                        aria-label={`Remover ponto ${i + 1}`}
                      >
                        <IconTrash width={14} height={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="rounded-field border border-dashed border-base-300 p-8 text-center">
            <IconMapPin
              width={28}
              height={28}
              className="mx-auto text-base-content/25"
            />
            <p className="mt-3 text-sm text-base-content/60">
              Nenhum ponto ainda. Adicione manualmente, importe um CSV/JSON ou
              busque por endereço.
            </p>
            <div className="mt-4 flex justify-center gap-2">
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => onAddPoint()}
              >
                <IconPlus width={15} height={15} />
                Adicionar
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => fileInputRef.current?.click()}
              >
                <IconUpload width={15} height={15} />
                Importar
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
