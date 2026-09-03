package dev.vgandolfi.solver.orchestrator.application.service;

import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.vgandolfi.solver.orchestrator.application.dto.messaging.JobCreatedMessage;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.solver.orchestrator.application.dto.messaging.JobResultMessage;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.solver.orchestrator.application.mapper.JobMapper;
import dev.vgandolfi.solver.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.solver.orchestrator.domain.entity.Usage;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.exception.JobNotFoundException;
import dev.vgandolfi.solver.orchestrator.domain.repository.OptimizationJobRepository;
import dev.vgandolfi.solver.orchestrator.domain.repository.UsageRepository;
import dev.vgandolfi.solver.orchestrator.infrastructure.messaging.JobMessageProducer;
import dev.vgandolfi.solver.orchestrator.infrastructure.s3.S3Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso de orquestração de jobs de otimização.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobApplicationService {

    private static final String INPUTS_KEY_PREFIX = "inputs";

    private final OptimizationJobRepository jobRepository;
    private final UsageRepository usageRepository;
    private final S3Storage s3Storage;
    private final JobMessageProducer messageProducer;
    private final JobMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final WebhookNotifier webhookNotifier;

    /**
     * Cria um job: valida o JSON, faz upload do input para o S3, persiste o
     * OptimizationJob e o Usage (nessa ordem, por causa da FK
     * usages.optimization_job_id → optimization_jobs) e publica a mensagem.
     */
    @Transactional
    public JobResponse createJob(JobType type, String inputJson, String webhookUrl,
                                 String ipAddress, String userAgent) {
        validateJson(inputJson);

        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();

        String inputPath = INPUTS_KEY_PREFIX + "/" + jobId + ".json";
        s3Storage.uploadJson(inputPath, inputJson);

        OptimizationJob job = OptimizationJob.builder()
                .id(jobId)
                .type(type)
                .status(JobStatus.PENDING)
                .inputPath(inputPath)
                .webhookUrl(webhookUrl)
                .createdAt(now)
                .build();
        jobRepository.save(job);

        usageRepository.save(Usage.builder()
                .id(UUID.randomUUID())
                .optimizationJobId(jobId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestedAt(now)
                .build());

        messageProducer.publishJob(new JobCreatedMessage(jobId, type, inputPath, jobId, webhookUrl));

        log.info("job_created id={} type={}", jobId, type);
        return jobMapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID id) {
        return jobMapper.toStatusResponse(findJob(id));
    }

    @Transactional(readOnly = true)
    public String getOutputJson(UUID id) {
        OptimizationJob job = findJob(id);
        if (job.getStatus() != JobStatus.DONE) {
            throw new IllegalStateException("Output not ready for job " + id);
        }
        return s3Storage.downloadJson(job.getOutputPath());
    }

    @Transactional(readOnly = true)
    public String getInputJson(UUID id) {
        return s3Storage.downloadJson(findJob(id).getInputPath());
    }

    /**
     * Processa uma mensagem de resultado do worker, aplicando as transições de
     * status e disparando o webhook assíncrono (fail-open) quando configurado.
     */
    @Transactional
    public void handleJobResult(JobResultMessage message) {
        OptimizationJob job = findJob(message.routingJobId());
        JobStatus resolved = resolveStatus(message.solverStatus());
        switch (resolved) {
            case DONE -> job.markCompleted(message.outputPath(), message.durationMillis());
            case ERROR -> job.markFailed(message.errorMessage(), message.durationMillis());
            case RUNNING -> job.markRunning();
            default -> log.warn("unexpected_solver_status job={} solverStatus={}", message.routingJobId(), message.solverStatus());
        }
        jobRepository.save(job);

        if (job.getWebhookUrl() != null && !job.getWebhookUrl().isBlank()) {
            webhookNotifier.notifyJobFinished(job, jobMapper.toStatusResponse(job));
        }
        log.info("job_result_handled id={} status={}", job.getId(), job.getStatus());
    }

    private void validateJson(String input) {
        try {
            objectMapper.readTree(input);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("input must be a valid JSON document", ex);
        }
    }

    private JobStatus resolveStatus(String solverStatus) {
        if (solverStatus == null) {
            return JobStatus.ERROR;
        }
        String status = solverStatus.toUpperCase(Locale.ROOT);
        return switch (status) {
            case "OPTIMAL", "FEASIBLE" -> JobStatus.DONE;
            case "ERROR", "TIMEOUT", "INFEASIBLE" -> JobStatus.ERROR;
            case "RUNNING" -> JobStatus.RUNNING;
            default -> JobStatus.ERROR;
        };
    }

    private OptimizationJob findJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
    }
}