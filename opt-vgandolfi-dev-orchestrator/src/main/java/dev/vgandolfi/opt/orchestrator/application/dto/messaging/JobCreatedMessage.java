package dev.vgandolfi.opt.orchestrator.application.dto.messaging;

import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;

import java.util.UUID;

/**
 * Mensagem publicada para o worker Python (fila de requisição).
 * Campos camelCase conforme o contrato do worker: routingJobId, jobType,
 * inputPath, userId, webhookUrl.
 */
public record JobCreatedMessage(
        UUID routingJobId,
        JobType jobType,
        String inputPath,
        UUID userId,
        String webhookUrl) {
}