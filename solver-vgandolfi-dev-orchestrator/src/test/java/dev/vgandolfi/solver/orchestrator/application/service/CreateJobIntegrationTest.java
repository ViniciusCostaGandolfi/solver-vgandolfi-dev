package dev.vgandolfi.solver.orchestrator.application.service;

import dev.vgandolfi.solver.orchestrator.application.dto.messaging.JobCreatedMessage;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.solver.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.solver.orchestrator.domain.entity.Usage;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;
import dev.vgandolfi.solver.orchestrator.domain.repository.OptimizationJobRepository;
import dev.vgandolfi.solver.orchestrator.domain.repository.UsageRepository;
import dev.vgandolfi.solver.orchestrator.infrastructure.messaging.JobMessageProducer;
import dev.vgandolfi.solver.orchestrator.infrastructure.s3.S3Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste de integração com repositórios JPA reais (H2, profile test) e as
 * dependências externas mockadas.
 *
 * <p>O Hibernate não emite FK para {@code usages.optimization_job_id} porque a
 * entidade {@link Usage} expõe apenas a coluna UUID (sem relação JPA). Por isso
 * este teste recria explicitamente a constraint do V1__init.sql
 * (usages.optimization_job_id → optimization_jobs.id) via JdbcTemplate, para
 * reproduzir o schema real do Postgres e garantir que {@code createJob} persiste
 * o job ANTES do usage — com a ordem antiga o insert do usage violaria a FK
 * (DataIntegrityViolationException), como acontece em produção.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreateJobIntegrationTest {

    private static final String VALID_INPUT = "{\"matrixType\":\"EUCLIDIAN\",\"origin\":{\"lat\":-23.5,\"lng\":-46.6}}";

    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private OptimizationJobRepository jobRepository;
    @Autowired private UsageRepository usageRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private S3Storage s3Storage;
    @MockitoBean private JobMessageProducer messageProducer;
    @MockitoBean private WebhookNotifier webhookNotifier;

    @BeforeEach
    void enforceForeignKey() {
        jdbcTemplate.execute("""
                ALTER TABLE usages ADD CONSTRAINT IF NOT EXISTS usages_optimization_job_id_fkey
                FOREIGN KEY (optimization_job_id) REFERENCES optimization_jobs (id)
                """);
    }

    @Test
    void createJobPersistsJobAndUsageAndPublishesMessage() {
        when(s3Storage.uploadJson(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = jobApplicationService.createJob(
                JobType.TSP, VALID_INPUT, null, "127.0.0.1", "integration-test");

        UUID jobId = response.id();
        assertThat(response.status()).isEqualTo(JobStatus.PENDING);

        Optional<OptimizationJob> job = jobRepository.findById(jobId);
        assertThat(job).isPresent();
        assertThat(job.get().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.get().getInputPath()).isEqualTo("inputs/" + jobId + ".json");

        List<Usage> usages = usageRepository.findAll();
        assertThat(usages).anySatisfy(usage -> {
            assertThat(usage.getOptimizationJobId()).isEqualTo(jobId);
            assertThat(usage.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(usage.getUserAgent()).isEqualTo("integration-test");
        });

        verify(s3Storage).uploadJson(eq("inputs/" + jobId + ".json"), eq(VALID_INPUT));

        ArgumentCaptor<JobCreatedMessage> captor = ArgumentCaptor.forClass(JobCreatedMessage.class);
        verify(messageProducer).publishJob(captor.capture());
        assertThat(captor.getValue().routingJobId()).isEqualTo(jobId);
        assertThat(captor.getValue().inputPath()).isEqualTo("inputs/" + jobId + ".json");
        assertThat(captor.getValue().jobType()).isEqualTo(JobType.TSP);
    }
}