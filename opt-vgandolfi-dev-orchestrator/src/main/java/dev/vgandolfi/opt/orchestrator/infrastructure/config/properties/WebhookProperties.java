package dev.vgandolfi.opt.orchestrator.infrastructure.config.properties;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do mecanismo de retry de webhooks.
 *
 * @param retryDelays delays entre tentativas de retry (após a 1ª falha),
 *                    ex.: {@code 30s,5m,1h,24h}
 * @param maxAttempts total de tentativas de envio (1 imediata + retries);
 *                    ao esgotar, o webhook vai para a DLQ estática no S3
 */
@ConfigurationProperties(prefix = "app.webhook")
public record WebhookProperties(List<Duration> retryDelays, int maxAttempts) {
}