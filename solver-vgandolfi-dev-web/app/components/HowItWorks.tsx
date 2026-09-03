import { IconLayers, IconMapPin, IconRoute } from "./icons";
import { SectionHeading } from "./ui";

const STEPS = [
  {
    n: "1",
    title: "Informe os pontos",
    desc: "Digite lat/lng, importe um CSV/JSON ou busque por endereço.",
    icon: <IconMapPin width={20} height={20} />,
  },
  {
    n: "2",
    title: "Escolha e otimize",
    desc: "Selecione o tipo de problema (TSP, VRP ou matriz) e clique em Otimizar.",
    icon: <IconRoute width={20} height={20} />,
  },
  {
    n: "3",
    title: "Visualize o resultado",
    desc: "Rota desenhada no mapa, resumo em números e JSON para baixar.",
    icon: <IconLayers width={20} height={20} />,
  },
];

export function HowItWorks() {
  return (
    <section
      id="como-funciona"
      className="mx-auto max-w-6xl scroll-mt-24 px-4 py-14 sm:px-6"
    >
      <SectionHeading
        eyebrow="Fluxo"
        title="Como funciona"
        desc="Três passos e o resultado aparece no mapa e no JSON."
      />
      <div className="grid gap-3 sm:grid-cols-3">
        {STEPS.map((s, i) => (
          <div
            key={s.n}
            className="card card-border animate-fade-up"
            style={{ animationDelay: `${i * 90}ms` }}
          >
            <div className="card-body gap-2 p-5">
              <div className="flex items-center justify-between">
                <span className="grid h-10 w-10 place-items-center rounded-box bg-base-200 text-base-content/70">
                  {s.icon}
                </span>
                <span className="font-display text-3xl font-bold text-base-content/10">
                  {s.n}
                </span>
              </div>
              <h3 className="mt-2 font-display font-semibold">{s.title}</h3>
              <p className="text-sm text-base-content/60">{s.desc}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
