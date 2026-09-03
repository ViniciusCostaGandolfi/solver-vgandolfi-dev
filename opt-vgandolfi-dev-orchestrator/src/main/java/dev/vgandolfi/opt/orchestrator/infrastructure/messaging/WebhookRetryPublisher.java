package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.WebhookRetryMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Publica mensagens de retry de webhook na fila {@code webhook.retry.queue}
 * com TTL por mensagem ({@code x-message-ttl} via expiration). Ao expirar, o
 * DLX da fila de retry roteia a mensagem de volta para o exchange com a
 * routing key da fila de processamento — o padrão clássico de retry com delay
 * do RabbitMQ (sem plugin delayed-message), que sobrevive a restart da API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryPublisher {

    static final String RETRY_QUEUE = "webhook.retry.queue";

    private final RabbitTemplate rabbitTemplate;

    public void scheduleRetry(WebhookRetryMessage message, Duration delay) {
        MessagePostProcessor expiration = m -> {
            m.getMessageProperties().setExpiration(String.valueOf(delay.toMillis()));
            return m;
        };
        rabbitTemplate.convertAndSend(RETRY_QUEUE, message, expiration);
        log.info("webhook_retry_scheduled job={} attempt={} delayMs={}",
                message.jobId(), message.attemptCount() + 1, delay.toMillis());
    }
}