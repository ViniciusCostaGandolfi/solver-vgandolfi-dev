import { useMemo, useRef, useState } from "react";
import type { MapPoint } from "../../components/MapCanvas";
import {
  parsePointsFile,
  pointsFromGeocode,
  uid,
} from "../format";
import type { OriginState } from "../payload";
import { isValidLatLng, parseCoord } from "../payload";
import type { MatrixType, PointRow, ProblemType, VehicleRow } from "../types";
import type { ToastKind } from "./useToast";

const DEFAULT_POINTS: PointRow[] = [
  { id: "p1", name: "Av. Paulista, 1578", lat: "-23.5614", lng: "-46.6559", volumeLiters: "10", weightKg: "120" },
  { id: "p2", name: "Mercado Municipal", lat: "-23.5415", lng: "-46.6293", volumeLiters: "25", weightKg: "340" },
  { id: "p3", name: "Parque Ibirapuera", lat: "-23.5874", lng: "-46.6576", volumeLiters: "5", weightKg: "40" },
];

const DEFAULT_VEHICLES: VehicleRow[] = [
  { id: "v1", name: "Van", maxDeliveries: "10", maxWeightKg: "1000", maxVolumeLiters: "200" },
  { id: "v2", name: "Carro", maxDeliveries: "5", maxWeightKg: "400", maxVolumeLiters: "80" },
];

const DEFAULT_ORIGIN: OriginState = {
  name: "",
  lat: "-23.5505",
  lng: "-46.6333",
};

interface GeocodeSelection {
  formattedAddress: string;
  latitude: number;
  longitude: number;
}

export interface UseOptimizerState {
  problemType: ProblemType;
  matrixType: MatrixType;
  origin: OriginState;
  points: PointRow[];
  vehicles: VehicleRow[];
  webhookUrl: string;
  geoBusy: boolean;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  mapPoints: MapPoint[];

  setProblemType: (type: ProblemType) => void;
  setMatrixType: (type: MatrixType) => void;
  setOrigin: React.Dispatch<React.SetStateAction<OriginState>>;
  setWebhookUrl: (url: string) => void;

  addPoint: (data?: Partial<PointRow>) => void;
  updatePoint: (id: string, patch: Partial<PointRow>) => void;
  removePoint: (id: string) => void;
  addVehicle: () => void;
  updateVehicle: (id: string, patch: Partial<VehicleRow>) => void;
  removeVehicle: (id: string) => void;
  clearPoints: () => void;

  handleOriginGeocode: (r: GeocodeSelection) => void;
  handleAddByAddress: (r: GeocodeSelection) => void;
  useMyLocation: () => void;
  handleFile: (file: File | undefined) => Promise<void>;
  handlePointDrag: (id: string, lat: number, lng: number) => void;
  handleMapClick: (lat: number, lng: number) => void;
  /** Modo explícito de adicionar ponto no mapa (ligado = clique adiciona). */
  addPointMode: boolean;
  toggleAddPointMode: () => void;
}

interface UseOptimizerStateArgs {
  showToast: (msg: string, kind: ToastKind) => void;
}

/** Estado editável do formulário de otimização (pontos, veículos, origem). */
export function useOptimizerState({
  showToast,
}: UseOptimizerStateArgs): UseOptimizerState {
  const [problemType, setProblemType] = useState<ProblemType>("TSP");
  const [matrixType, setMatrixType] = useState<MatrixType>("EUCLIDIAN");
  const [origin, setOrigin] = useState<OriginState>(DEFAULT_ORIGIN);
  const [points, setPoints] = useState<PointRow[]>(DEFAULT_POINTS);
  const [vehicles, setVehicles] = useState<VehicleRow[]>(DEFAULT_VEHICLES);
  const [webhookUrl, setWebhookUrl] = useState("");
  const [geoBusy, setGeoBusy] = useState(false);
  /** Modo adicionar no mapa: quando ligado, um clique no mapa insere um ponto. */
  const [addPointMode, setAddPointMode] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  /* ---------------------- CRUD de pontos ---------------------- */
  const addPoint = (data: Partial<PointRow> = {}) => {
    setPoints((prev) => [
      ...prev,
      {
        id: uid(),
        name: data.name ?? "",
        lat: data.lat ?? "",
        lng: data.lng ?? "",
        volumeLiters: data.volumeLiters ?? "0",
        weightKg: data.weightKg ?? "0",
      },
    ]);
  };

  const updatePoint = (id: string, patch: Partial<PointRow>) => {
    setPoints((prev) => prev.map((p) => (p.id === id ? { ...p, ...patch } : p)));
  };

  const removePoint = (id: string) => {
    setPoints((prev) => prev.filter((p) => p.id !== id));
  };

  /* ---------------------- CRUD de veículos ---------------------- */
  const addVehicle = () => {
    setVehicles((prev) => [
      ...prev,
      {
        id: uid(),
        name: `Veículo ${prev.length + 1}`,
        maxDeliveries: "10",
        maxWeightKg: "1000",
        maxVolumeLiters: "100",
      },
    ]);
  };

  const updateVehicle = (id: string, patch: Partial<VehicleRow>) => {
    setVehicles((prev) => prev.map((v) => (v.id === id ? { ...v, ...patch } : v)));
  };

  const removeVehicle = (id: string) => {
    setVehicles((prev) => prev.filter((v) => v.id !== id));
  };

  /* ---------------------- geocodificação ---------------------- */
  const handleOriginGeocode = (r: GeocodeSelection) => {
    setOrigin({
      name: r.formattedAddress.slice(0, 60),
      lat: String(r.latitude),
      lng: String(r.longitude),
    });
    showToast("Origem definida pelo endereço.", "success");
  };

  const handleAddByAddress = (r: GeocodeSelection) => {
    const p = pointsFromGeocode(r);
    addPoint(p);
    showToast("Ponto adicionado pelo endereço.", "success");
  };

  const useMyLocation = () => {
    if (!("geolocation" in navigator)) {
      showToast("Geolocalização não disponível neste navegador.", "info");
      return;
    }
    setGeoBusy(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setOrigin((prev) => ({
          ...prev,
          lat: String(pos.coords.latitude),
          lng: String(pos.coords.longitude),
        }));
        setGeoBusy(false);
        showToast("Origem definida pela sua localização.", "success");
      },
      () => {
        setGeoBusy(false);
        showToast("Não foi possível obter a localização.", "error");
      },
      { timeout: 8000, maximumAge: 60000 },
    );
  };

  /* ---------------------- importação de arquivos ---------------------- */
  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    try {
      const parsed = await parsePointsFile(file);
      if (parsed.rows.length === 0) {
        showToast("Nenhum ponto encontrado no arquivo.", "error");
        return;
      }
      setPoints((prev) => [
        ...prev,
        ...parsed.rows.map((r) => ({
          id: uid(),
          name: r.name,
          lat: r.lat,
          lng: r.lng,
          volumeLiters: "0",
          weightKg: "0",
        })),
      ]);
      showToast(
        `${parsed.rows.length} ponto(s) importado(s) de ${parsed.sourceLabel}.`,
        "success",
      );
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Arquivo inválido.", "error");
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  /* ---------------------- mapa ---------------------- */
  const mapPoints: MapPoint[] = useMemo(() => {
    const list: MapPoint[] = [];
    if (problemType !== "DISTANCE_MATRIX") {
      const olat = parseCoord(origin.lat);
      const olng = parseCoord(origin.lng);
      if (isValidLatLng(olat, olng)) {
        list.push({
          id: "__origin__",
          name: origin.name || "Origem",
          lat: olat!,
          lng: olng!,
          kind: "origin",
        });
      }
    }
    for (const p of points) {
      const la = parseCoord(p.lat);
      const ln = parseCoord(p.lng);
      if (la !== null && ln !== null) {
        list.push({ id: p.id, name: p.name, lat: la, lng: ln, kind: "stop" });
      }
    }
    return list;
  }, [origin, points, problemType]);

  const handlePointDrag = (id: string, lat: number, lng: number) => {
    if (id === "__origin__") {
      setOrigin((prev) => ({ ...prev, lat: String(lat), lng: String(lng) }));
    } else {
      updatePoint(id, { lat: String(lat), lng: String(lng) });
    }
  };

  const handleMapClick = (lat: number, lng: number) => {
    addPoint({ lat: String(lat), lng: String(lng) });
    showToast("Ponto adicionado no mapa.", "success");
  };

  const toggleAddPointMode = () => setAddPointMode((m) => !m);

  const clearPoints = () => {
    setPoints([]);
  };

  return {
    problemType,
    matrixType,
    origin,
    points,
    vehicles,
    webhookUrl,
    geoBusy,
    fileInputRef,
    mapPoints,
    setProblemType,
    setMatrixType,
    setOrigin,
    setWebhookUrl,
    addPoint,
    updatePoint,
    removePoint,
    addVehicle,
    updateVehicle,
    removeVehicle,
    clearPoints,
    handleOriginGeocode,
    handleAddByAddress,
    useMyLocation,
    handleFile,
    handlePointDrag,
    handleMapClick,
    addPointMode,
    toggleAddPointMode,
  };
}
