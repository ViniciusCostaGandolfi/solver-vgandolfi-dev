package dev.vgandolfi.solver.orchestrator.application.dto.messaging;

import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mensagem de retry de webhook publicada na fila {@code webhook.retry.queue}.
 * Carrega TODA a informação necessária para reprocessar: o payload do webhook
 * (preservado entre tentativas), a contagem de falhas já registradas e os
 * timestamps/erros acumulados de cada tentativa.
 *
 * @param jobId            id do job
 * @param jobType          tipo do job (TSP/VRP/DISTANCE_MATRIX)
 * @param webhookUrl       URL de callback do cliente
 * @param payload          corpo JSON enviado ao webhook (preservado entre tentativas)
 * @param attemptCount     nº de tentativas que JÁ falharam (= attemptErrors.size())
 * @param attemptTimestamps timestamps das tentativas que falharam
 * @param attemptErrors    mensagens de erro de cada tentativa que falhou
 */
public record WebhookRetryMessage(
        UUID jobId,
        JobType jobType,
        String webhookUrl,
        Map<String, Object> payload,
        int attemptCount,
        List<Instant> attemptTimestamps,
        List<String> attemptErrors) {
}