package dev.vgandolfi.opt.orchestrator.application.service;

import dev.vgandolfi.opt.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.opt.orchestrator.application.dto.messaging.WebhookRetryMessage;
import dev.vgandolfi.opt.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.WebhookProperties;
import dev.vgandolfi.opt.orchestrator.infrastructure.messaging.WebhookRetryPublisher;
import dev.vgandolfi.opt.orchestrator.infrastructure.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispara notificações webhook de forma assíncrona e fail-open: a tentativa
 * imediata é feita uma única vez e, em caso de falha, a mensagem de retry vai
 * para a fila RabbitMQ (TTL+DLX) — nunca quebra o fluxo do job.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookNotifier {

    private static final String ATTEMPT_ERROR = "webhook_dispatch_failed";

    private final WebhookDispatcher webhookDispatcher;
    private final WebhookRetryPublisher webhookRetryPublisher;
    private final WebhookProperties webhookProperties;

    @Async("webhookExecutor")
    public void notifyJobFinished(OptimizationJob job, JobStatusResponse status) {
        String webhookUrl = job.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        Map<String, Object> payload = buildPayload(job, status);
        if (webhookDispatcher.dispatch(webhookUrl, payload)) {
            log.info("webhook_sent job={} url={}", job.getId(), webhookUrl);
            return;
        }
        scheduleInitialRetry(job, webhookUrl, payload);
    }

    private Map<String, Object> buildPayload(OptimizationJob job, JobStatusResponse status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", job.getId());
        payload.put("status", status.status());
        payload.put("outputUrl", status.outputUrl());
        payload.put("errorMessage", status.errorMessage());
        return payload;
    }

    /** 1ª tentativa falhou: agenda o retry com o 1º delay da configuração. */
    private void scheduleInitialRetry(OptimizationJob job, String webhookUrl, Map<String, Object> payload) {
        if (webhookProperties.retryDelays().isEmpty()) {
            log.warn("webhook_failed_no_retry job={} url={}", job.getId(), webhookUrl);
            return;
        }
        WebhookRetryMessage retry = new WebhookRetryMessage(
                job.getId(), job.getType(), webhookUrl, payload, 1,
                new ArrayList<>(List.of(Instant.now())),
                new ArrayList<>(List.of(ATTEMPT_ERROR)));
        webhookRetryPublisher.scheduleRetry(retry, webhookProperties.retryDelays().get(0));
        log.warn("webhook_failed_retry_scheduled job={} url={} nextDelay={}",
                job.getId(), webhookUrl, webhookProperties.retryDelays().get(0));
    }
}