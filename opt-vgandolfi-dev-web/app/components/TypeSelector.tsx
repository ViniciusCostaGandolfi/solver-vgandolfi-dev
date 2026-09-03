import type { ProblemType } from "../lib/types";
import { IconCheck, IconGrid, IconRoute, IconTruck } from "./icons";
import { SectionHeading } from "./ui";

interface TypeDef {
  id: ProblemType;
  title: string;
  short: string;
  desc: string;
  icon: React.ReactNode;
}

export const TYPE_DEFS: TypeDef[] = [
  {
    id: "TSP",
    title: "Rota única (TSP)",
    short: "TSP",
    desc: "Menor caminho para visitar todos os pontos e voltar à origem.",
    icon: <IconRoute width={22} height={22} />,
  },
  {
    id: "VRP",
    title: "Frota de veículos (VRP)",
    short: "VRP",
    desc: "Distribua entregas entre veículos respeitando capacidade e limites.",
    icon: <IconTruck width={22} height={22} />,
  },
  {
    id: "DISTANCE_MATRIX",
    title: "Matriz de distâncias",
    short: "Matriz",
    desc: "Distâncias entre todos os pares de pontos, sem gerar rota.",
    icon: <IconGrid width={22} height={22} />,
  },
];

interface TypeSelectorProps {
  problemType: ProblemType;
  onSelect: (type: ProblemType) => void;
}

export function TypeSelector({ problemType, onSelect }: TypeSelectorProps) {
  return (
    <>
      <SectionHeading
        eyebrow="Problema"
        title="O que você quer resolver?"
        desc="Cada tipo gera um payload diferente. Você só preenche os pontos."
      />
      <div className="grid gap-3 sm:grid-cols-3">
        {TYPE_DEFS.map((t) => {
          const selected = problemType === t.id;
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => onSelect(t.id)}
              className={`card card-border group relative text-left transition-all duration-200 ${
                selected
                  ? "border-primary bg-primary/5 shadow-sm"
                  : "hover:border-base-300 hover:bg-base-200/50"
              }`}
              aria-pressed={selected}
            >
              <div className="card-body gap-2 p-5">
                <div className="flex items-center justify-between">
                  <span
                    className={`grid h-11 w-11 place-items-center rounded-box transition-colors ${
                      selected
                        ? "bg-primary text-primary-content"
                        : "bg-base-200 text-base-content/70 group-hover:text-base-content"
                    }`}
                  >
                    {t.icon}
                  </span>
                  {selected && (
                    <span className="badge badge-primary badge-sm gap-1">
                      <IconCheck width={11} height={11} />
                      selecionado
                    </span>
                  )}
                </div>
                <h3 className="mt-2 font-display font-semibold">{t.title}</h3>
                <p className="text-sm text-base-content/60">{t.desc}</p>
              </div>
            </button>
          );
        })}
      </div>
    </>
  );
}
