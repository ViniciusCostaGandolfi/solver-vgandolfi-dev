package dev.vgandolfi.solver.orchestrator.application.dto.job.response;

import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta de polling de status de um job ({@code GET /api/v1/jobs/{id}}):
 * TODOS os campos da entidade {@code OptimizationJob} + as URLs públicas.
 * Timestamps serializam como ISO-8601 (Jackson 3).
 */
public record JobStatusResponse(
        UUID id,
        JobType type,
        JobStatus status,
        String inputUrl,
        String outputUrl,
        String statusUrl,
        String webhookUrl,
        String errorMessage,
        Long processingTimeMs,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String inputPath,
        String outputPath) {
}