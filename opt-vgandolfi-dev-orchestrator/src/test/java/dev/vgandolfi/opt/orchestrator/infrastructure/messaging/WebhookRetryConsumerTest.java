package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.WebhookRetryMessage;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.WebhookProperties;
import dev.vgandolfi.opt.orchestrator.infrastructure.s3.S3Storage;
import dev.vgandolfi.opt.orchestrator.infrastructure.webhook.WebhookDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa o {@link WebhookRetryConsumer}: sucesso para a cadeia, republicação com
 * o próximo delay em falha intermediária e escrita da DLQ estática no S3
 * ({@code dlq/{jobType}/{jobId}.json}) após esgotar as tentativas.
 */
class WebhookRetryConsumerTest {

    private static final WebhookProperties PROPERTIES = new WebhookProperties(
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofDays(1)), 5);

    private WebhookDispatcher dispatcher;
    private WebhookRetryPublisher publisher;
    private S3Storage s3Storage;
    private WebhookRetryConsumer consumer;

    @BeforeEach
    void setUp() {
        dispatcher = mock(WebhookDispatcher.class);
        publisher = mock(WebhookRetryPublisher.class);
        s3Storage = mock(S3Storage.class);
        consumer = new WebhookRetryConsumer(dispatcher, publisher, PROPERTIES, s3Storage, new JsonMapper());
    }

    private WebhookRetryMessage message(JobType type, UUID id, int failures) {
        List<Instant> timestamps = new ArrayList<>();
        for (int i = 0; i < failures; i++) {
            timestamps.add(Instant.now().minusSeconds(i));
        }
        List<String> errors = new ArrayList<>(Collections.nCopies(failures, "err"));
        return new WebhookRetryMessage(id, type, "https://hooks.example.com/cb",
                Map.of("jobId", id, "status", "DONE"), failures, timestamps, errors);
    }

    @Test
    void retrySucceedsAndStopsTheChain() {
        UUID id = UUID.randomUUID();
        WebhookRetryMessage message = message(JobType.VRP, id, 1);
        when(dispatcher.dispatch(message.webhookUrl(), message.payload())).thenReturn(true);

        consumer.onRetry(message);

        verify(publisher, never()).scheduleRetry(any(), any());
        verify(s3Storage, never()).uploadJson(any(), any());
    }

    @Test
    void intermediateFailureSchedulesNextRetryWithNextDelay() {
        UUID id = UUID.randomUUID();
        WebhookRetryMessage message = message(JobType.VRP, id, 1);
        when(dispatcher.dispatch(message.webhookUrl(), message.payload())).thenReturn(false);

        consumer.onRetry(message);

        ArgumentCaptor<WebhookRetryMessage> captor = ArgumentCaptor.forClass(WebhookRetryMessage.class);
        // message.attemptCount=1 → 2 falhas totais → próximo delay = 5m (índice 1).
        verify(publisher).scheduleRetry(captor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(captor.getValue().attemptCount()).isEqualTo(2);
        assertThat(captor.getValue().attemptErrors()).hasSize(2);
        assertThat(captor.getValue().attemptTimestamps()).hasSize(2);
        verify(s3Storage, never()).uploadJson(any(), any());
    }

    @Test
    void lastFailureWritesDlqToS3WithFullInformation() {
        UUID id = UUID.randomUUID();
        WebhookRetryMessage message = message(JobType.VRP, id, 4);
        when(dispatcher.dispatch(message.webhookUrl(), message.payload())).thenReturn(false);

        consumer.onRetry(message);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3Storage).uploadJson(keyCaptor.capture(), contentCaptor.capture());
        verify(publisher, never()).scheduleRetry(any(), any());

        assertThat(keyCaptor.getValue()).isEqualTo("dlq/vrp/" + id + ".json");

        JsonNode node = new JsonMapper().readTree(contentCaptor.getValue());
        assertThat(node.get("jobId").asText()).isEqualTo(id.toString());
        assertThat(node.get("jobType").asText()).isEqualTo("VRP");
        assertThat(node.get("webhookUrl").asText()).isEqualTo("https://hooks.example.com/cb");
        assertThat(node.get("attempts").asInt()).isEqualTo(5);
        assertThat(node.get("attemptTimestamps")).hasSize(5);
        assertThat(node.get("attemptErrors")).hasSize(5);
        assertThat(node.get("payload").get("jobId").asText()).isEqualTo(id.toString());
    }

    @Test
    void dlqKeyUsesJobTypeSegment() {
        UUID id = UUID.randomUUID();
        WebhookRetryMessage message = message(JobType.DISTANCE_MATRIX, id, 4);
        when(dispatcher.dispatch(message.webhookUrl(), message.payload())).thenReturn(false);

        consumer.onRetry(message);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3Storage).uploadJson(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo("dlq/distance-matrix/" + id + ".json");
    }

    @Test
    void retryAfterFirstFailureUsesThirtySecondsDelay() {
        UUID id = UUID.randomUUID();
        // attemptCount=0 não ocorre na prática (o notifier sempre registra a 1ª
        // falha), mas o consumer deve ser seguro: 1 falha total → 30s (índice 0).
        WebhookRetryMessage message = message(JobType.VRP, id, 0);
        when(dispatcher.dispatch(message.webhookUrl(), message.payload())).thenReturn(false);

        consumer.onRetry(message);

        verify(publisher).scheduleRetry(any(), eq(Duration.ofSeconds(30)));
    }
}