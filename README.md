# opt-vgandolfi-dev

Ferramenta pública e gratuita para resolver **TSP**, **VRP** e **Distance Matrix** de forma assíncrona (polling ou webhook).

- **UI**: página única em React + daisyUI, com mapa (Leaflet), geocoding por endereço e editor de entrada.
- **API**: `POST /api/v1/jobs/{jobType}` — um endpoint por tipo de problema, com DTO de entrada dedicado.
- **Assíncrono por design**: o job é aceito (202), processado em background e o resultado fica disponível via polling ou webhook.

## Arquitetura

| Serviço | Stack | Papel |
|---|---|---|
| `orchestrator-service/` | Spring Boot 4 (Java 25, DDD) | API REST, jobs, rate-limit, S3, fila, webhook |
| `opt-worker-solver/` | FastAPI (Python) | Solvers: TSP (LKH), VRP (ALNS) e Distance Matrix (euclidiana ou OSRM) |
| `opt-vgandolfi-dev-web/` | React Router + Vite + daisyUI + Leaflet | UI em página única |

Fluxo: `UI/curl → API (orchestrator) → RabbitMQ → worker → S3 → resultado`. Inputs e outputs trafegam sempre como objetos no MinIO (S3); o banco guarda apenas os metadados do job.

## Subindo a stack local

Pré-requisitos: Docker, Java 25 + Maven (wrapper incluso), Python 3.12+.

```bash
# 1. Infraestrutura (Postgres, RabbitMQ, MinIO)
docker compose up -d

# 2. API (Spring Boot)
cd orchestrator-service
export DB_USER=opt-vgandolfi-dev DB_PASSWORD=opt-vgandolfi-dev DB_NAME=opt-vgandolfi-dev
export RABBIT_USER=opt-vgandolfi-dev RABBIT_PASSWORD=opt-vgandolfi-dev
./mvnw spring-boot:run

# 3. Worker (FastAPI) — em outro terminal
cd opt-worker-solver
source /tmp/opt-worker-venv/bin/activate   # ou seu venv
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001

# 4. UI (dev) — em outro terminal
cd opt-vgandolfi-dev-web
npm install && npm run dev
```

> O bucket MinIO (`opt-vgandolfi-dev`) precisa existir. Crie uma vez com o cliente `mc` ou o script Python do worker.

## Usando a API

Base URL: `http://localhost:8080/api/v1`. Toda chamada de job retorna **202** com o `id`; o resultado é obtido por polling ou webhook.

### TSP — Caixeiro Viajante

```bash
curl -X POST http://localhost:8080/api/v1/jobs/tsp \
  -H 'Content-Type: application/json' \
  -d '{
    "input": {
      "origin": { "lat": -23.5505, "lng": -46.6333 },
      "stops": [
        { "id": "A", "name": "Cliente A", "location": { "lat": -23.5614, "lng": -46.6559 } },
        { "id": "B", "name": "Cliente B", "location": { "lat": -23.5540, "lng": -46.6300 } },
        { "id": "C", "name": "Cliente C", "location": { "lat": -23.5470, "lng": -46.6400 } }
      ],
      "matrixType": "EUCLIDIAN"
    }
  }'
```

### VRP — Roteirização de Veículos

```bash
curl -X POST http://localhost:8080/api/v1/jobs/vrp \
  -H 'Content-Type: application/json' \
  -d '{
    "input": {
      "origin": { "lat": -23.5505, "lng": -46.6333 },
      "clients": [
        { "id": "c1", "name": "Cliente 1", "location": { "lat": -23.5614, "lng": -46.6559 }, "volumeLiters": 10, "weightKg": 50 },
        { "id": "c2", "name": "Cliente 2", "location": { "lat": -23.5540, "lng": -46.6300 }, "volumeLiters": 20, "weightKg": 30 }
      ],
      "vehicles": [
        { "name": "Van", "maxDeliveries": 5, "maxWeightKg": 1000, "maxVolumeLiters": 200 }
      ],
      "matrixType": "EUCLIDIAN"
    }
  }'
```

### Distance Matrix

```bash
curl -X POST http://localhost:8080/api/v1/jobs/distance-matrix \
  -H 'Content-Type: application/json' \
  -d '{
    "input": {
      "coordinates": [
        { "lat": -23.5505, "lng": -46.6333 },
        { "lat": -23.5614, "lng": -46.6559 },
        { "lat": -23.5540, "lng": -46.6300 }
      ],
      "matrixType": "STREET"
    }
  }'
```

### Polling do resultado

```bash
# 1. Pega o id do 202 (ex.: "d46e1455-...")
JOB_ID=SEU_JOB_ID

# 2. Consulta o status até DONE
curl http://localhost:8080/api/v1/jobs/$JOB_ID
# → {"id":"...","status":"DONE","outputUrl":"http://localhost:8080/api/v1/jobs/<id>/output",...}

# 3. Baixa o output
curl http://localhost:8080/api/v1/jobs/$JOB_ID/output
```

Status possíveis: `PENDING`, `RUNNING`, `DONE`, `ERROR`. Enquanto não estiver `DONE`, `GET /{id}/output` retorna **409**.

### Webhook (opcional)

Envie `webhookUrl` como chave **ao lado** de `input` (não dentro dele). Ao finalizar, a API faz um `POST` para a URL com:

```json
{ "jobId": "...", "status": "DONE", "outputUrl": "http://localhost:8080/api/v1/jobs/<id>/output", "errorMessage": null }
```

```bash
curl -X POST http://localhost:8080/api/v1/jobs/tsp \
  -H 'Content-Type: application/json' \
  -d '{
    "webhookUrl": "https://seuservidor.com/resultado",
    "input": { "...": "..." }
  }'
```

## Referência de endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/jobs/tsp` | Cria job TSP (202) |
| POST | `/api/v1/jobs/vrp` | Cria job VRP (202) |
| POST | `/api/v1/jobs/distance-matrix` | Cria job de matriz de distâncias (202) |
| GET | `/api/v1/jobs/{id}` | Status do job |
| GET | `/api/v1/jobs/{id}/input` | Input original (do S3) |
| GET | `/api/v1/jobs/{id}/output` | Resultado (409 se não concluído) |
| GET | `/api/v1/geo/geocode?address=...` | Autocomplete de endereço (Nominatim) |
| GET | `/api/v1/geo/reverse?lat=...&lng=...` | Reverse geocoding |
| GET | `/api/v1/health` | Healthcheck |

Campos válidos: `lat` ∈ [-90, 90], `lng` ∈ [-180, 180]; `matrixType`: `EUCLIDIAN` (default) ou `STREET` (via OSRM). Erros de validação → `400 {"error":"Validation failed","fields":{...}}`.

### Rate limit (por IP)

- **10** jobs/min em `POST /api/v1/jobs/*`
- **100** polls/min em `GET /api/v1/jobs/*`
- **30** chamadas/min em `GET /api/v1/geo/*`

Ultrapassou → `429 {"error":"Rate limit exceeded","retryAfterSeconds":60}`. Configurável via env `RATE_LIMIT_JOBS_PER_MINUTE`, `RATE_LIMIT_POLLS_PER_MINUTE`, `RATE_LIMIT_GEO_PER_MINUTE`.

## Testes

```bash
# Backend (68+ testes, JaCoCo ≥ 70%)
cd orchestrator-service && ./mvnw clean test

# Worker (18 testes)
cd opt-worker-solver && python -m pytest tests/ -v

# UI
cd opt-vgandolfi-dev-web && npm run typecheck && npm run build
```

## Variáveis de ambiente

Ver `.env` na raiz e `orchestrator-service/src/main/resources/application.yml`. As principais: `DB_*`, `RABBIT_*`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET_NAME`, `APP_BACKEND_URL`, `NOMINATIM_URL`, `RATE_LIMIT_*`.