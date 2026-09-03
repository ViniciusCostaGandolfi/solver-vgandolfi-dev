package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.WebhookRetryMessage;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Testa o {@link WebhookRetryPublisher}: publica na fila de retry com TTL por
 * mensagem ({@code x-message-ttl} via expiration) igual ao delay configurado.
 */
@ExtendWith(MockitoExtension.class)
class WebhookRetryPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @Test
    void schedulesRetryWithPerMessageTtl() {
        WebhookRetryPublisher publisher = new WebhookRetryPublisher(rabbitTemplate);
        WebhookRetryMessage message = new WebhookRetryMessage(UUID.randomUUID(), JobType.VRP,
                "https://hooks.example.com/cb", Map.of("jobId", UUID.randomUUID()),
                2, List.of(), List.of());

        publisher.scheduleRetry(message, Duration.ofMinutes(5));

        ArgumentCaptor<MessagePostProcessor> mppCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(WebhookRetryPublisher.RETRY_QUEUE), eq(message), mppCaptor.capture());

        Message processed = mppCaptor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(processed.getMessageProperties().getExpiration()).isEqualTo("300000");
    }
}