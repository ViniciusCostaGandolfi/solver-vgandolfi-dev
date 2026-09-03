package dev.vgandolfi.solver.orchestrator.application.dto.messaging;

import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;

import java.util.UUID;

/**
 * Mensagem de resultado publicada pelo worker Python na fila de resultado.
 * Todos os campos são opcionais (nulls permitidos) exceto routingJobId/jobType,
 * conforme o comportamento do worker em cenários de erro.
 */
public record JobResultMessage(
        UUID routingJobId,
        JobType jobType,
        String inputPath,
        String outputPath,
        Long durationMillis,
        String solverStatus,
        String errorMessage,
        String warningMessage,
        String solverType,
        String modelName,
        UUID userId,
        Double totalDistanceMeters,
        Integer totalStops,
        Integer totalRoutes) {
}