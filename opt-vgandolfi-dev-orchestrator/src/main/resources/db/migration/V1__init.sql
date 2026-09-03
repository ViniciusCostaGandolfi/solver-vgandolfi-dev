-- V1: schema inicial do orchestrator-service
-- Tabela de jobs de otimização (TSP/VRP/DISTANCE_MATRIX)
CREATE TABLE optimization_jobs (
    id                UUID PRIMARY KEY,
    type              VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    input_path        VARCHAR(500) NOT NULL,
    output_path       VARCHAR(500),
    webhook_url       VARCHAR(500),
    error_message     TEXT,
    processing_time_ms BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ
);

CREATE INDEX idx_optimization_jobs_status ON optimization_jobs (status);
CREATE INDEX idx_optimization_jobs_created_at ON optimization_jobs (created_at);

-- Tabela de uso por requisição (rate-limit / auditoria)
CREATE TABLE usages (
    id                 UUID PRIMARY KEY,
    optimization_job_id UUID        NOT NULL REFERENCES optimization_jobs (id),
    ip_address         VARCHAR(45)  NOT NULL,
    user_agent         VARCHAR(500),
    requested_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_usages_ip_address ON usages (ip_address);