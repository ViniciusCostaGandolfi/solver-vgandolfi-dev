import type { ReactNode } from "react";
import { apiHealthUrl, apiSwaggerUrl } from "../lib/format";
import { CodeBlock } from "./CodeBlock";
import {
  IconExternal,
  IconGrid,
  IconInfo,
  IconRefresh,
  IconRoute,
  IconTruck,
  IconZap,
} from "./icons";
import { SectionHeading } from "./ui";

interface ApiField {
  name: string;
  desc: string;
  required?: boolean;
}

function FlowStep({ n, title, desc }: { n: string; title: string; desc: string }) {
  return (
    <div className="card card-border">
      <div className="card-body gap-2">
        <div className="flex items-center gap-2">
          <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-primary/10 font-mono text-xs font-bold text-primary">
            {n}
          </span>
          <p className="font-display text-sm font-semibold">{title}</p>
        </div>
        <p className="text-xs text-base-content/60">{desc}</p>
      </div>
    </div>
  );
}

function EndpointCard({
  icon,
  title,
  desc,
  route,
  body,
  bodyDesc,
  response,
  responseDesc,
  curl,
  fields,
  note,
}: {
  icon: ReactNode;
  title: string;
  desc: string;
  route: string;
  body: string;
  bodyDesc?: string;
  response: string;
  responseDesc?: string;
  curl: string;
  fields: ApiField[];
  note?: string;
}) {
  return (
    <div className="card card-border mt-6">
      <div className="card-body gap-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-box bg-primary/10 text-primary">
              {icon}
            </span>
            <div>
              <h4 className="font-display text-base font-semibold">{title}</h4>
              <p className="mt-0.5 max-w-2xl text-sm text-base-content/60">
                {desc}
              </p>
            </div>
          </div>
          <span className="badge badge-ghost badge-sm font-mono">{route}</span>
        </div>

        <div className="grid min-w-0 gap-4 lg:grid-cols-2">
          <CodeBlock
            title="Corpo do request"
            description={bodyDesc}
            code={body}
          />
          <CodeBlock
            title="Resposta 202"
            description={responseDesc}
            code={response}
          />
        </div>

        <CodeBlock title="curl de exemplo" code={curl} />

        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-base-content/40">
            Campos
          </p>
          <div className="grid gap-2 sm:grid-cols-2">
            {fields.map((f) => (
              <div
                key={f.name}
                className="flex items-start justify-between gap-3 rounded-field border border-base-200 bg-base-100 px-3 py-2"
              >
                <div className="min-w-0">
                  <code className="font-mono text-[11px] text-base-content/80">
                    {f.name}
                  </code>
                  <p className="text-xs text-base-content/55">{f.desc}</p>
                </div>
                <span
                  className={`badge badge-xs shrink-0 ${
                    f.required ? "badge-success badge-soft" : "badge-ghost"
                  }`}
                >
                  {f.required ? "obrigatório" : "opcional"}
                </span>
              </div>
            ))}
          </div>
        </div>

        {note && (
          <p className="flex items-center gap-1.5 text-xs text-base-content/55">
            <IconInfo width={13} height={13} />
            {note}
          </p>
        )}
      </div>
    </div>
  );
}

const TSP_BODY = `{
  "webhookUrl": "https://seu-servidor.com/hook",
  "input": {
    "matrixType": "EUCLIDIAN",
    "origin": { "lat": -23.5505, "lng": -46.6333 },
    "stops": [
      { "id": "p1", "name": "Parada 1", "location": { "lat": -23.5614, "lng": -46.6559 } },
      { "id": "p2", "name": "Parada 2", "location": { "lat": -23.5874, "lng": -46.6576 } },
      { "id": "p3", "name": "Parada 3", "location": { "lat": -23.5343, "lng": -46.6339 } }
    ]
  }
}`;

const TSP_CURL = `curl -X POST http://localhost:8080/api/v1/jobs/tsp \\
  -H 'Content-Type: application/json' \\
  -d '{
    "webhookUrl": "https://seu-servidor.com/hook",
    "input": {
      "matrixType": "EUCLIDIAN",
      "origin": { "lat": -23.5505, "lng": -46.6333 },
      "stops": [
        { "id": "p1", "name": "Parada 1", "location": { "lat": -23.5614, "lng": -46.6559 } },
        { "id": "p2", "name": "Parada 2", "location": { "lat": -23.5874, "lng": -46.6576 } },
        { "id": "p3", "name": "Parada 3", "location": { "lat": -23.5343, "lng": -46.6339 } }
      ]
    }
  }'`;

const VRP_BODY = `{
  "webhookUrl": "https://seu-servidor.com/hook",
  "input": {
    "matrixType": "EUCLIDIAN",
    "origin": { "lat": -23.5505, "lng": -46.6333 },
    "clients": [
      { "id": "c1", "name": "Cliente 1", "location": { "lat": -23.5614, "lng": -46.6559 }, "volumeLiters": 10, "weightKg": 120 },
      { "id": "c2", "name": "Cliente 2", "location": { "lat": -23.5874, "lng": -46.6576 }, "volumeLiters": 25, "weightKg": 340 }
    ],
    "vehicles": [
      { "name": "Van", "maxDeliveries": 10, "maxWeightKg": 1000, "maxVolumeLiters": 200 },
      { "name": "Carro", "maxDeliveries": 5, "maxWeightKg": 400, "maxVolumeLiters": 80 }
    ]
  }
}`;

const VRP_CURL = `curl -X POST http://localhost:8080/api/v1/jobs/vrp \\
  -H 'Content-Type: application/json' \\
  -d '{
    "webhookUrl": "https://seu-servidor.com/hook",
    "input": {
      "matrixType": "EUCLIDIAN",
      "origin": { "lat": -23.5505, "lng": -46.6333 },
      "clients": [
        { "id": "c1", "name": "Cliente 1", "location": { "lat": -23.5614, "lng": -46.6559 }, "volumeLiters": 10, "weightKg": 120 },
        { "id": "c2", "name": "Cliente 2", "location": { "lat": -23.5874, "lng": -46.6576 }, "volumeLiters": 25, "weightKg": 340 }
      ],
      "vehicles": [
        { "name": "Van", "maxDeliveries": 10, "maxWeightKg": 1000, "maxVolumeLiters": 200 },
        { "name": "Carro", "maxDeliveries": 5, "maxWeightKg": 400, "maxVolumeLiters": 80 }
      ]
    }
  }'`;

const MATRIX_BODY = `{
  "webhookUrl": "https://seu-servidor.com/hook",
  "input": {
    "matrixType": "EUCLIDIAN",
    "coordinates": [
      { "lat": -23.5614, "lng": -46.6559 },
      { "lat": -23.5874, "lng": -46.6576 },
      { "lat": -23.5343, "lng": -46.6339 }
    ]
  }
}`;

const MATRIX_CURL = `curl -X POST http://localhost:8080/api/v1/jobs/distance-matrix \\
  -H 'Content-Type: application/json' \\
  -d '{
    "webhookUrl": "https://seu-servidor.com/hook",
    "input": {
      "matrixType": "EUCLIDIAN",
      "coordinates": [
        { "lat": -23.5614, "lng": -46.6559 },
        { "lat": -23.5874, "lng": -46.6576 },
        { "lat": -23.5343, "lng": -46.6339 }
      ]
    }
  }'`;

const CREATED_202 = `{
  "id": "c8d3525c-ff82-4ec9-9c50-d4f753e85502",
  "type": "TSP",
  "status": "PENDING",
  "inputUrl": "http://localhost:8080/api/v1/jobs/.../input",
  "outputUrl": null,
  "statusUrl": "http://localhost:8080/api/v1/jobs/...",
  "createdAt": "2026-09-02T01:02:36Z",
  "startedAt": null,
  "finishedAt": null,
  "processingTimeMs": null
}`;

const STATUS_200 = `{
  "id": "2603bbcc-5142-4e09-8cd4-0c96453e8056",
  "type": "TSP",
  "status": "DONE",
  "inputUrl": "http://localhost:8080/api/v1/jobs/.../input",
  "outputUrl": "http://localhost:8080/api/v1/jobs/.../output",
  "statusUrl": "http://localhost:8080/api/v1/jobs/...",
  "webhookUrl": null,
  "errorMessage": null,
  "processingTimeMs": 2,
  "createdAt": "2026-09-02T01:02:36Z",
  "startedAt": null,
  "finishedAt": "2026-09-02T01:02:36Z",
  "inputPath": "inputs/....json",
  "outputPath": "solutions/....json"
}`;

const OUTPUT_200 = `{
  "optimized_stops": [
    {
      "id": "p1",
      "customer_name": "Parada 1",
      "address": { "latitude": -23.5614, "longitude": -46.6559 }
    }
  ],
  "route_line": [
    { "lat": -23.5505, "lng": -46.6333 },
    { "lat": -23.5614, "lng": -46.6559 }
  ],
  "distance_meters": 33595.49,
  "time_to_solve_ms": 0.23
}`;

const TSP_FIELDS: ApiField[] = [
  { name: "webhookUrl", desc: "URL que recebe um POST quando o job termina.", required: false },
  { name: "input.matrixType", desc: '"EUCLIDIAN" (linha reta) ou "STREET" (OSRM).', required: true },
  { name: "input.origin", desc: "{ lat, lng } do depósito (origem da rota).", required: true },
  { name: "input.stops[]", desc: "Paradas: id, name e location { lat, lng }. Mínimo 2.", required: true },
];

const VRP_FIELDS: ApiField[] = [
  { name: "webhookUrl", desc: "URL que recebe um POST quando o job termina.", required: false },
  { name: "input.matrixType", desc: '"EUCLIDIAN" (linha reta) ou "STREET" (OSRM).', required: true },
  { name: "input.origin", desc: "{ lat, lng } do depósito (saída das rotas).", required: true },
  { name: "input.clients[]", desc: "Clientes: id, name, location { lat, lng }, volumeLiters (L) e weightKg (kg). Mínimo 1.", required: true },
  { name: "input.vehicles[]", desc: "Veículos: name, maxDeliveries, maxWeightKg e maxVolumeLiters. Mínimo 1.", required: true },
];

const MATRIX_FIELDS: ApiField[] = [
  { name: "webhookUrl", desc: "URL que recebe um POST quando o job termina.", required: false },
  { name: "input.matrixType", desc: '"EUCLIDIAN" (linha reta) ou "STREET" (OSRM).', required: true },
  { name: "input.coordinates[]", desc: "Pontos { lat, lng } — a matriz é entre todos os pares. Mínimo 2.", required: true },
];

const STATUS_VALUES: Array<{ value: string; label: string; badge: string }> = [
  { value: "PENDING", label: "Na fila", badge: "badge-info badge-soft" },
  { value: "RUNNING", label: "Processando", badge: "badge-warning badge-soft" },
  { value: "DONE", label: "Concluído", badge: "badge-success badge-soft" },
  { value: "ERROR", label: "Erro", badge: "badge-error badge-soft" },
];

export function ApiDocs() {
  return (
    <section id="api" className="mx-auto max-w-6xl scroll-mt-24 px-4 py-16 sm:px-6">
      <SectionHeading
        eyebrow="Integração"
        title="Usar via API"
        desc="A mesma otimização disponível por HTTP. Jobs são assíncronos: envie, faça polling e receba o resultado."
      />

      {/* Destaque: Swagger UI */}
      <div className="card card-border mb-8 overflow-hidden">
        <div className="card-body flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-box bg-primary/10 text-primary">
              <IconZap width={18} height={18} />
            </span>
            <div>
              <p className="font-display text-base font-semibold">
                Documentação interativa e testável
              </p>
              <p className="mt-0.5 max-w-xl text-sm text-base-content/60">
                Explore e teste todos os endpoints (jobs, status, resultado e
                saúde) direto no Swagger UI.
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <a
              href={apiSwaggerUrl()}
              target="_blank"
              rel="noreferrer"
              className="btn btn-primary gap-1.5"
            >
              <IconExternal width={16} height={16} />
              Abrir Swagger UI
            </a>
            <a
              href={apiHealthUrl()}
              target="_blank"
              rel="noreferrer"
              className="btn btn-outline btn-sm gap-1.5"
            >
              <IconExternal width={14} height={14} />
              Saúde da API (/health)
            </a>
          </div>
        </div>
      </div>

      {/* Fluxo assíncrono */}
      <div className="grid min-w-0 gap-6 lg:grid-cols-3">
        <FlowStep
          n="1"
          title="Criar o job"
          desc="POST /api/v1/jobs/{tipo} — retorna 202 com o id do job em PENDING."
        />
        <FlowStep
          n="2"
          title="Acompanhar o status"
          desc="GET /api/v1/jobs/{id} — faça polling até DONE ou ERROR."
        />
        <FlowStep
          n="3"
          title="Receber o resultado"
          desc="GET /api/v1/jobs/{id}/output — ou receba um POST no webhook."
        />
      </div>

      <h3 className="mt-10 font-display text-xl font-bold tracking-tight">
        Por tipo de problema
      </h3>

      <EndpointCard
        icon={<IconRoute width={18} height={18} />}
        title="TSP — Rota única"
        desc="Caixeiro viajante: visita todas as paradas partindo da origem, na ordem que minimiza o trajeto, e retorna ao início."
        route="POST /api/v1/jobs/tsp"
        body={TSP_BODY}
        bodyDesc="Envelope { webhookUrl, input }. webhookUrl é opcional."
        response={CREATED_202}
        responseDesc="202 Created — guarde o id para acompanhar."
        curl={TSP_CURL}
        fields={TSP_FIELDS}
        note="Resultado: paradas otimizadas (optimized_stops), distância total em metros (distance_meters) e a rota (route_line)."
      />

      <EndpointCard
        icon={<IconTruck width={18} height={18} />}
        title="VRP — Frota de veículos"
        desc="Roteiriza a entrega distribuindo os clientes entre os veículos, respeitando o máximo de paradas, peso e volume de cada um."
        route="POST /api/v1/jobs/vrp"
        body={VRP_BODY}
        bodyDesc="Envelope { webhookUrl, input } com clients e vehicles."
        response={CREATED_202}
        responseDesc="202 Created — o campo type vem como VRP."
        curl={VRP_CURL}
        fields={VRP_FIELDS}
        note="Resultado: uma rota por veículo (routes), com clientes, distância, peso e volume de cada rota."
      />

      <EndpointCard
        icon={<IconGrid width={18} height={18} />}
        title="Matriz de distâncias"
        desc="Calcula as distâncias entre todos os pares de pontos informados — não usa origem: a matriz cobre todos os pares."
        route="POST /api/v1/jobs/distance-matrix"
        body={MATRIX_BODY}
        bodyDesc="Envelope { webhookUrl, input } com as coordenadas."
        response={CREATED_202}
        responseDesc="202 Created — o campo type vem como DISTANCE_MATRIX."
        curl={MATRIX_CURL}
        fields={MATRIX_FIELDS}
        note="Resultado: matriz N×N de distâncias em metros (matrix) mais as coordenadas."
      />

      {/* Status / resultado / webhook */}
      <div className="card card-border mt-6">
        <div className="card-body gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="grid h-9 w-9 shrink-0 place-items-center rounded-box bg-success/15 text-success">
                <IconRefresh width={18} height={18} />
              </span>
              <div>
                <h4 className="font-display text-base font-semibold">
                  Acompanhar o job e receber o resultado
                </h4>
                <p className="mt-0.5 max-w-2xl text-sm text-base-content/60">
                  Faça polling no status até DONE (ou ERROR) e depois baixe o
                  resultado. Todos os campos da entidade job vêm no status.
                </p>
              </div>
            </div>
            <span className="badge badge-ghost badge-sm font-mono">
              GET /api/v1/jobs/{`{id}`}
            </span>
          </div>

          <div className="flex flex-wrap gap-1.5">
            {STATUS_VALUES.map((s) => (
              <span key={s.value} className={`badge badge-sm gap-1.5 ${s.badge}`}>
                {s.label}
                <span className="font-mono text-[10px] opacity-70">{s.value}</span>
              </span>
            ))}
          </div>

          <div className="grid min-w-0 gap-4 lg:grid-cols-2">
            <CodeBlock
              title="Resposta 200 (status)"
              description="status: PENDING → RUNNING → DONE | ERROR."
              code={STATUS_200}
            />
            <CodeBlock
              title="Resultado — GET /api/v1/jobs/{id}/output"
              description="Disponível quando o status é DONE (exemplo TSP)."
              code={OUTPUT_200}
            />
          </div>

          <div role="note" className="alert alert-info alert-soft">
            <IconInfo width={18} height={18} />
            <div>
              <p className="text-sm font-medium">Webhook opcional</p>
              <p className="text-xs opacity-80">
                Se webhookUrl for enviado no POST, o backend chama essa URL com
                um POST quando o job termina — não precisa de polling.
              </p>
            </div>
          </div>
        </div>
      </div>

      <div role="status" className="alert alert-info alert-soft mt-8 max-w-2xl">
        <IconInfo width={18} height={18} />
        <div>
          <p className="text-sm font-medium">Rate limit por IP</p>
          <p className="text-xs opacity-80">
            ~10 jobs/min, ~100 consultas/min e ~30 geocodificações/min. Em
            excesso, a API responde 429.
          </p>
        </div>
      </div>
    </section>
  );
}