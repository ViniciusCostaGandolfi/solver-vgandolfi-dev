package dev.vgandolfi.solver.orchestrator.application.mapper;

import org.springframework.stereotype.Component;

import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.solver.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.AppProperties;
import lombok.RequiredArgsConstructor;

/**
 * Converte entidades de domínio em DTOs de resposta, montando as URLs públicas
 * a partir de {@code app.base-url}.
 */
@Component
@RequiredArgsConstructor
public class JobMapper {

    private final AppProperties appProperties;

    public JobResponse toResponse(OptimizationJob job) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                baseUrl() + "/api/v1/jobs/" + job.getId() + "/input",
                outputUrl(job),
                baseUrl() + "/api/v1/jobs/" + job.getId(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getProcessingTimeMs());
    }

    public JobStatusResponse toStatusResponse(OptimizationJob job) {
        return new JobStatusResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                baseUrl() + "/api/v1/jobs/" + job.getId() + "/input",
                outputUrl(job),
                baseUrl() + "/api/v1/jobs/" + job.getId(),
                job.getWebhookUrl(),
                job.getErrorMessage(),
                job.getProcessingTimeMs(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getInputPath(),
                job.getOutputPath());
    }

    private String outputUrl(OptimizationJob job) {
        if (job.getStatus() != JobStatus.DONE) {
            return null;
        }
        return baseUrl() + "/api/v1/jobs/" + job.getId() + "/output";
    }

    private String baseUrl() {
        String url = appProperties.baseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}