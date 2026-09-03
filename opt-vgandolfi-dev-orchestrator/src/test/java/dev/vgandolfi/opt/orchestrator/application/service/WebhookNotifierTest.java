package dev.vgandolfi.opt.orchestrator.application.service;

import dev.vgandolfi.opt.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.opt.orchestrator.application.dto.messaging.WebhookRetryMessage;
import dev.vgandolfi.opt.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.WebhookProperties;
import dev.vgandolfi.opt.orchestrator.infrastructure.messaging.WebhookRetryPublisher;
import dev.vgandolfi.opt.orchestrator.infrastructure.webhook.WebhookDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookNotifierTest {

    @Mock private WebhookDispatcher webhookDispatcher;
    @Mock private WebhookRetryPublisher webhookRetryPublisher;

    private static final WebhookProperties PROPERTIES = new WebhookProperties(
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofDays(1)), 5);

    private WebhookNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new WebhookNotifier(webhookDispatcher, webhookRetryPublisher, PROPERTIES);
    }

    private OptimizationJob jobWithWebhook(String url) {
        return OptimizationJob.builder()
                .id(UUID.randomUUID())
                .type(JobType.VRP)
                .status(JobStatus.DONE)
                .inputPath("inputs/x.json")
                .outputPath("solutions/y.json")
                .webhookUrl(url)
                .createdAt(Instant.now())
                .build();
    }

    private JobStatusResponse statusFor(UUID id) {
        return new JobStatusResponse(id, JobType.VRP, JobStatus.DONE,
                "http://localhost:8080/api/v1/jobs/" + id + "/input",
                "http://localhost:8080/api/v1/jobs/" + id + "/output",
                "http://localhost:8080/api/v1/jobs/" + id,
                null, null, 100L,
                Instant.now(), Instant.now(), Instant.now(),
                "inputs/x.json", "solutions/y.json");
    }

    @Test
    void dispatchesWebhookOnSuccessWithoutSchedulingRetry() {
        OptimizationJob job = jobWithWebhook("https://hooks.example.com/cb");
        when(webhookDispatcher.dispatch(eq(job.getWebhookUrl()), anyMap())).thenReturn(true);

        notifier.notifyJobFinished(job, statusFor(job.getId()));

        verify(webhookDispatcher).dispatch(eq(job.getWebhookUrl()), anyMap());
        verify(webhookRetryPublisher, never()).scheduleRetry(any(), any());
    }

    @Test
    void schedulesInitialRetryWhenDispatchFails() {
        OptimizationJob job = jobWithWebhook("https://hooks.example.com/cb");
        when(webhookDispatcher.dispatch(eq(job.getWebhookUrl()), anyMap())).thenReturn(false);

        notifier.notifyJobFinished(job, statusFor(job.getId()));

        ArgumentCaptor<WebhookRetryMessage> captor = ArgumentCaptor.forClass(WebhookRetryMessage.class);
        verify(webhookRetryPublisher).scheduleRetry(captor.capture(), eq(Duration.ofSeconds(30)));

        WebhookRetryMessage retry = captor.getValue();
        assertThat(retry.jobId()).isEqualTo(job.getId());
        assertThat(retry.jobType()).isEqualTo(JobType.VRP);
        assertThat(retry.webhookUrl()).isEqualTo(job.getWebhookUrl());
        assertThat(retry.attemptCount()).isEqualTo(1);
        assertThat(retry.attemptTimestamps()).hasSize(1);
        assertThat(retry.attemptErrors()).hasSize(1);
        assertThat(retry.payload()).containsEntry("jobId", job.getId());
    }

    @Test
    void doesNothingWhenWebhookUrlIsNull() {
        notifier.notifyJobFinished(jobWithWebhook(null), statusFor(UUID.randomUUID()));

        verify(webhookDispatcher, never()).dispatch(anyString(), anyMap());
        verify(webhookRetryPublisher, never()).scheduleRetry(any(), any());
    }

    @Test
    void doesNothingWhenWebhookUrlIsBlank() {
        notifier.notifyJobFinished(jobWithWebhook("   "), statusFor(UUID.randomUUID()));

        verify(webhookDispatcher, never()).dispatch(anyString(), anyMap());
        verify(webhookRetryPublisher, never()).scheduleRetry(any(), any());
    }
}