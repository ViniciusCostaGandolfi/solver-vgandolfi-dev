package dev.vgandolfi.opt.orchestrator.domain.entity;

import dev.vgandolfi.opt.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizationJobTest {

    private OptimizationJob pendingJob() {
        return OptimizationJob.builder()
                .id(UUID.randomUUID())
                .type(JobType.TSP)
                .status(JobStatus.PENDING)
                .inputPath("inputs/job.json")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void markRunningSetsStatusAndStartedAt() {
        OptimizationJob job = pendingJob();

        job.markRunning();

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    void markCompletedWithProvidedDuration() {
        OptimizationJob job = pendingJob();
        job.markRunning();

        job.markCompleted("solutions/out.json", 1234L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getOutputPath()).isEqualTo("solutions/out.json");
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getProcessingTimeMs()).isEqualTo(1234L);
    }

    @Test
    void markCompletedComputesDurationWhenNotProvided() {
        OptimizationJob job = pendingJob();
        job.setStartedAt(Instant.now().minusSeconds(5));

        job.markCompleted("solutions/out.json", null);

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getProcessingTimeMs()).isNotNull().isGreaterThanOrEqualTo(4000L);
    }

    @Test
    void markFailedWithoutStartedAtKeepsProcessingTimeNull() {
        OptimizationJob job = pendingJob();

        job.markFailed("boom", null);

        assertThat(job.getStatus()).isEqualTo(JobStatus.ERROR);
        assertThat(job.getErrorMessage()).isEqualTo("boom");
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getProcessingTimeMs()).isNull();
    }

    @Test
    void markFailedWithProvidedDuration() {
        OptimizationJob job = pendingJob();
        job.setStartedAt(Instant.now().minusSeconds(2));

        job.markFailed("timeout", 2500L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.ERROR);
        assertThat(job.getErrorMessage()).isEqualTo("timeout");
        assertThat(job.getProcessingTimeMs()).isEqualTo(2500L);
    }
}