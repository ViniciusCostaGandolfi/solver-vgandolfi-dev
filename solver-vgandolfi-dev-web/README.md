# opt · Frontend Web

Interface web de otimização de rotas — TSP (caixeiro viajante), VRP (frota de veículos) e matriz de distâncias. Frontend em React Router Framework Mode + Vite + Tailwind CSS 4 + daisyUI 5 + Leaflet, consumindo a API do [orchestrator-service](../orchestrator-service).

## O que é

Uma ferramenta gratuita de roteirização que roda direto no navegador: você informa os pontos (por lat/lng, endereço ou importação de CSV/JSON), escolhe o tipo de problema e recebe o resultado otimizado com mapa, resumo numérico e JSON para baixar ou integrar via webhook.

## Pré-requisitos

- Node.js ≥ 22 (o template exige ≥ 22.22.0; versões um pouco menores funcionam com aviso)
- Backend em execução em `http://localhost:8080` (o [`orchestrator-service`](../orchestrator-service), subido via `docker compose` na raiz do projeto)

## Rodando em dev

```bash
npm install
npm run dev
```

A aplicação fica disponível em `http://localhost:5173`. O `vite.config.ts` já configura um proxy que encaminha `/api` e `/health` para o backend em `http://localhost:8080`, então nenhuma variável de ambiente é necessária para desenvolvimento local.

## Build e typecheck

```bash
npm run typecheck   # gera os tipos do React Router + checagem do TypeScript
npm run build       # build de produção (client + SSR) em ./build
npm run start       # serve o build de produção
```

## Produção (Docker)

O `Dockerfile` recebe a URL da API via build arg `VITE_API_URL` (ex.: `http://localhost:8080/api/v1`). Em produção, o endpoint `/health` e os exemplos de `curl` usam essa mesma base automaticamente.

## Estrutura de pastas

```
app/
├── components/          # Componentes de UI da página única
│   ├── AddressSearch.tsx
│   ├── ApiDocs.tsx
│   ├── CodeBlock.tsx
│   ├── CopyButton.tsx
│   ├── Footer.tsx
│   ├── Hero.tsx
│   ├── HowItWorks.tsx
│   ├── icons.tsx
│   ├── JobStatusCard.tsx
│   ├── MapCanvas.tsx
│   ├── MapPanel.tsx
│   ├── Navbar.tsx
│   ├── OptimizerForm.tsx
│   ├── ResultPanels.tsx
│   ├── SubmitBar.tsx
│   ├── TypeSelector.tsx
│   └── ui.tsx            # SectionHeading, StatusBadge, InfoRow
├── lib/                  # Lógica e utilitários
│   ├── api.ts            # Cliente HTTP do backend (contrato de API)
│   ├── format.ts         # Formatação, CSV/JSON, URLs de API
│   ├── labels.ts         # Rótulos de tipos/matriz
│   ├── output.ts         # Type guards e parsing do resultado do solver
│   ├── payload.ts        # Builders de input + validação
│   ├── types.ts          # Tipos compartilhados
│   └── hooks/            # Hooks de estado
│       ├── useOptimizerState.ts
│       ├── useRoutingJob.ts
│       ├── useTheme.ts
│       └── useToast.ts
├── routes/
│   └── home.tsx          # Página única (composição das seções)
├── app.css               # Temas optlight/optdark e utilitários
├── root.tsx              # Layout raiz (tema, fontes)
└── routes.ts             # Configuração de rotas (índice → home)
```

A página é composta por componentes de seção (`Navbar`, `Hero`, `HowItWorks`, `TypeSelector`, `OptimizerForm`, `MapPanel`, `SubmitBar`, `JobStatusCard`, `ResultPanels`, `ApiDocs`, `Footer`) e por hooks de lógica (`useOptimizerState` para o formulário, `useRoutingJob` para submissão/polling/resultado).
