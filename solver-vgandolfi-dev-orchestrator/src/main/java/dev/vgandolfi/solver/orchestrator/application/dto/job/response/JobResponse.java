package dev.vgandolfi.solver.orchestrator.application.dto.job.response;

import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta de criação do job (202 Accepted). As URLs são públicas e montadas
 * a partir de {@code app.base-url}.
 */
public record JobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        String inputUrl,
        String outputUrl,
        String statusUrl,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long processingTimeMs) {
}