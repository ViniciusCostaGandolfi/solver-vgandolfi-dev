package dev.vgandolfi.solver.orchestrator.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vgandolfi.solver.orchestrator.application.dto.messaging.JobCreatedMessage;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.solver.orchestrator.application.dto.messaging.JobResultMessage;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.solver.orchestrator.application.mapper.JobMapper;
import dev.vgandolfi.solver.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.solver.orchestrator.domain.entity.Usage;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;
import dev.vgandolfi.solver.orchestrator.domain.exception.JobNotFoundException;
import dev.vgandolfi.solver.orchestrator.domain.repository.OptimizationJobRepository;
import dev.vgandolfi.solver.orchestrator.domain.repository.UsageRepository;
import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.AppProperties;
import dev.vgandolfi.solver.orchestrator.infrastructure.messaging.JobMessageProducer;
import dev.vgandolfi.solver.orchestrator.infrastructure.s3.S3Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    private static final String VALID_INPUT = "{\"matrixType\":\"EUCLIDIAN\",\"origin\":{\"lat\":-23.5,\"lng\":-46.6}}";

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private UsageRepository usageRepository;
    @Mock private S3Storage s3Storage;
    @Mock private JobMessageProducer messageProducer;
    @Mock private WebhookNotifier webhookNotifier;

    private JobApplicationService service;
    private JsonMapper objectMapper;
    private JobMapper jobMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new JsonMapper();
        jobMapper = new JobMapper(new AppProperties("http://localhost:8080", "https://nominatim.rotaslivres.com.br",
                "https://opencep.rotaslivres.com.br"));
        service = new JobApplicationService(jobRepository, usageRepository, s3Storage,
                messageProducer, jobMapper, objectMapper, webhookNotifier);
    }

    @Test
    void createJobUploadsSavesAndPublishes() {
        when(jobRepository.save(any(OptimizationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usageRepository.save(any(Usage.class))).thenAnswer(inv -> inv.getArgument(0));
        String webhookUrl = "https://hooks.example.com/cb";

        JobResponse response = service.createJob(JobType.TSP, VALID_INPUT, webhookUrl, "192.168.0.10", "test-agent");

        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
        assertThat(response.type()).isEqualTo(JobType.TSP);
        assertThat(response.id()).isNotNull();
        assertThat(response.statusUrl()).isEqualTo("http://localhost:8080/api/v1/jobs/" + response.id());
        assertThat(response.inputUrl()).isEqualTo("http://localhost:8080/api/v1/jobs/" + response.id() + "/input");
        assertThat(response.outputUrl()).isNull();

        ArgumentCaptor<Usage> usageCaptor = ArgumentCaptor.forClass(Usage.class);
        verify(usageRepository).save(usageCaptor.capture());
        assertThat(usageCaptor.getValue().getOptimizationJobId()).isEqualTo(response.id());
        assertThat(usageCaptor.getValue().getIpAddress()).isEqualTo("192.168.0.10");
        assertThat(usageCaptor.getValue().getUserAgent()).isEqualTo("test-agent");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3Storage).uploadJson(keyCaptor.capture(), eq(VALID_INPUT));
        assertThat(keyCaptor.getValue()).isEqualTo("inputs/" + response.id() + ".json");

        ArgumentCaptor<OptimizationJob> jobCaptor = ArgumentCaptor.forClass(OptimizationJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getInputPath()).isEqualTo(keyCaptor.getValue());
        assertThat(jobCaptor.getValue().getWebhookUrl()).isEqualTo("https://hooks.example.com/cb");

        ArgumentCaptor<JobCreatedMessage> msgCaptor = ArgumentCaptor.forClass(JobCreatedMessage.class);
        verify(messageProducer).publishJob(msgCaptor.capture());
        JobCreatedMessage published = msgCaptor.getValue();
        assertThat(published.routingJobId()).isEqualTo(response.id());
        assertThat(published.userId()).isEqualTo(response.id());
        assertThat(published.inputPath()).isEqualTo(keyCaptor.getValue());
        assertThat(published.jobType()).isEqualTo(JobType.TSP);
        assertThat(published.webhookUrl()).isEqualTo("https://hooks.example.com/cb");
    }

    @Test
    void createJobRejectsInvalidJson() {
        assertThatThrownBy(() -> service.createJob(JobType.VRP, "not-json", null, "1.2.3.4", "agent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JSON");

        verify(jobRepository, never()).save(any());
        verify(messageProducer, never()).publishJob(any());
    }

    @Test
    void getJobStatusReturnsMappedResponse() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = doneJob(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        JobStatusResponse response = service.getJobStatus(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.type()).isEqualTo(JobType.VRP);
        assertThat(response.status()).isEqualTo(JobStatus.DONE);
        assertThat(response.inputUrl()).isEqualTo("http://localhost:8080/api/v1/jobs/" + id + "/input");
        assertThat(response.outputUrl()).isEqualTo("http://localhost:8080/api/v1/jobs/" + id + "/output");
        assertThat(response.statusUrl()).isEqualTo("http://localhost:8080/api/v1/jobs/" + id);
        assertThat(response.webhookUrl()).isNull();
        assertThat(response.errorMessage()).isNull();
        assertThat(response.processingTimeMs()).isEqualTo(3000L);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.finishedAt()).isNotNull();
        assertThat(response.inputPath()).isEqualTo("inputs/" + id + ".json");
        assertThat(response.outputPath()).isEqualTo("solutions/" + id + ".json");
    }

    @Test
    void getJobStatusThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobStatus(id))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void getOutputJsonDownloadsFromStorageWhenDone() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = doneJob(id);
        job.setOutputPath("solutions/out.json");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(s3Storage.downloadJson("solutions/out.json")).thenReturn("{\"routes\":[]}");

        String output = service.getOutputJson(id);

        assertThat(output).isEqualTo("{\"routes\":[]}");
    }

    @Test
    void getOutputJsonThrowsWhenNotDone() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = pendingJob(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getOutputJson(id))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getInputJsonDownloadsFromStorage() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = pendingJob(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(s3Storage.downloadJson("inputs/" + id + ".json")).thenReturn("{\"matrixType\":\"EUCLIDIAN\"}");

        String input = service.getInputJson(id);

        assertThat(input).isEqualTo("{\"matrixType\":\"EUCLIDIAN\"}");
    }

    @Test
    void getInputJsonThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInputJson(id))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void handleJobResultMarksDoneAndFiresWebhook() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = pendingJob(id);
        job.setWebhookUrl("https://hooks.example.com/cb");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(OptimizationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleJobResult(resultMessage(id, "OPTIMAL", "solutions/out.json", 1200L, null));

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getOutputPath()).isEqualTo("solutions/out.json");
        assertThat(job.getProcessingTimeMs()).isEqualTo(1200L);
        assertThat(job.getFinishedAt()).isNotNull();
        verify(jobRepository).save(job);
        verify(webhookNotifier).notifyJobFinished(eq(job), any(JobStatusResponse.class));
    }

    @Test
    void handleJobResultMarksFailedOnTimeout() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = pendingJob(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(OptimizationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleJobResult(resultMessage(id, "TIMEOUT", null, 5000L, "solver timed out"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.ERROR);
        assertThat(job.getErrorMessage()).isEqualTo("solver timed out");
        assertThat(job.getProcessingTimeMs()).isEqualTo(5000L);
        verify(webhookNotifier, never()).notifyJobFinished(any(), any());
    }

    @Test
    void handleJobResultMarksRunningOnRunningStatus() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = pendingJob(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.handleJobResult(resultMessage(id, "RUNNING", null, null, null));

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartedAt()).isNotNull();
    }

    @Test
    void handleJobResultMapsUnknownSolverStatusToError() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = pendingJob(id);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.handleJobResult(resultMessage(id, "BANANA", null, null, "weird status"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.ERROR);
    }

    @Test
    void handleJobResultThrowsWhenJobNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleJobResult(resultMessage(id, "OPTIMAL", "x.json", 1L, null)))
                .isInstanceOf(JobNotFoundException.class);
    }

    private OptimizationJob pendingJob(UUID id) {
        return OptimizationJob.builder()
                .id(id)
                .type(JobType.TSP)
                .status(JobStatus.PENDING)
                .inputPath("inputs/" + id + ".json")
                .createdAt(Instant.now())
                .build();
    }

    private OptimizationJob doneJob(UUID id) {
        return OptimizationJob.builder()
                .id(id)
                .type(JobType.VRP)
                .status(JobStatus.DONE)
                .inputPath("inputs/" + id + ".json")
                .outputPath("solutions/" + id + ".json")
                .createdAt(Instant.now())
                .startedAt(Instant.now().minusSeconds(3))
                .finishedAt(Instant.now())
                .processingTimeMs(3000L)
                .build();
    }

    private JobResultMessage resultMessage(UUID id, String solverStatus, String outputPath,
                                           Long durationMillis, String errorMessage) {
        return new JobResultMessage(id, JobType.TSP, "inputs/" + id + ".json", outputPath,
                durationMillis, solverStatus, errorMessage, null, "LKH_TSP", "TspResponse",
                id, null, null, null);
    }
}