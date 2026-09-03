package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.WebhookRetryMessage;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.WebhookProperties;
import dev.vgandolfi.opt.orchestrator.infrastructure.s3.S3Storage;
import dev.vgandolfi.opt.orchestrator.infrastructure.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Consome os retries de webhook da fila {@code webhook.process.queue}: tenta o
 * envio; em falha, republica na fila de retry com o próximo delay (RabbitMQ
 * TTL+DLX) até esgotar as tentativas — aí grava o webhook na DLQ estática do
 * S3 em {@code dlq/{jobType}/{jobId}.json} com toda a informação acumulada.
 *
 * <p>Fail-open preservado: nenhum erro lançado aqui quebra o fluxo do job
 * (o {@code handleJobResult} já marcou DONE antes).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryConsumer {

    static final String PROCESS_QUEUE = "webhook.process.queue";
    static final String DLQ_KEY_PREFIX = "dlq/";

    private final WebhookDispatcher webhookDispatcher;
    private final WebhookRetryPublisher retryPublisher;
    private final WebhookProperties webhookProperties;
    private final S3Storage s3Storage;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = PROCESS_QUEUE)
    public void onRetry(WebhookRetryMessage message) {
        log.info("webhook_retry_received job={} failures={}", message.jobId(), message.attemptCount());
        if (webhookDispatcher.dispatch(message.webhookUrl(), message.payload())) {
            log.info("webhook_retry_succeeded job={}", message.jobId());
            return;
        }

        String error = "webhook_dispatch_failed";
        int totalAttempts = message.attemptCount() + 1;
        WebhookRetryMessage failed = withAttemptFailure(message, error);

        if (totalAttempts >= webhookProperties.maxAttempts()) {
            writeToDlq(failed, totalAttempts);
            return;
        }
        Duration nextDelay = webhookProperties.retryDelays().get(message.attemptCount());
        retryPublisher.scheduleRetry(failed, nextDelay);
    }

    /** Acrescenta o timestamp/erro da falha atual e incrementa a contagem. */
    private WebhookRetryMessage withAttemptFailure(WebhookRetryMessage message, String error) {
        List<Instant> timestamps = new ArrayList<>(message.attemptTimestamps());
        timestamps.add(Instant.now());
        List<String> errors = new ArrayList<>(message.attemptErrors());
        errors.add(error);
        return new WebhookRetryMessage(message.jobId(), message.jobType(), message.webhookUrl(),
                message.payload(), message.attemptCount() + 1, timestamps, errors);
    }

    /** Grava a DLQ estática no S3: {@code dlq/{jobType}/{jobId}.json}. */
    private void writeToDlq(WebhookRetryMessage message, int attempts) {
        String key = DLQ_KEY_PREFIX + jobTypeSegment(message.jobType()) + "/" + message.jobId() + ".json";
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("jobId", message.jobId());
            content.put("jobType", message.jobType());
            content.put("webhookUrl", message.webhookUrl());
            content.put("payload", message.payload());
            content.put("attempts", attempts);
            content.put("attemptTimestamps", message.attemptTimestamps());
            content.put("attemptErrors", message.attemptErrors());
            String json = objectMapper.writeValueAsString(content);
            s3Storage.uploadJson(key, json);
            log.warn("webhook_dlq_written key={} job={}", key, message.jobId());
        } catch (RuntimeException ex) {
            log.error("webhook_dlq_failed key={} job={} error={}", key, message.jobId(), ex.getMessage());
        }
    }

    /** TSP→tsp, VRP→vrp, DISTANCE_MATRIX→distance-matrix (segmento da URL/DLQ). */
    private String jobTypeSegment(JobType jobType) {
        return switch (jobType) {
            case TSP -> "tsp";
            case VRP -> "vrp";
            case DISTANCE_MATRIX -> "distance-matrix";
        };
    }
}