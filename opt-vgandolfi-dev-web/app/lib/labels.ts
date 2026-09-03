import type { MatrixType, ProblemType } from "./types";

export const TYPE_LABEL: Record<ProblemType, string> = {
  TSP: "Rota única (TSP)",
  VRP: "Frota de veículos (VRP)",
  DISTANCE_MATRIX: "Matriz de distâncias",
};

export const MATRIX_LABEL: Record<MatrixType, string> = {
  EUCLIDIAN: "Euclidiana (linha reta)",
  STREET: "Rodoviária (OSRM)",
};
