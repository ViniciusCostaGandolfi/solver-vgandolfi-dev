package dev.vgandolfi.solver.orchestrator.domain.repository;

import dev.vgandolfi.solver.orchestrator.domain.entity.OptimizationJob;
import dev.vgandolfi.solver.orchestrator.domain.entity.Usage;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OptimizationJobRepositoryTest {

    @Autowired private OptimizationJobRepository jobRepository;
    @Autowired private UsageRepository usageRepository;

    @Test
    void savesAndFindsOptimizationJob() {
        UUID id = UUID.randomUUID();
        OptimizationJob job = OptimizationJob.builder()
                .id(id)
                .type(JobType.TSP)
                .status(JobStatus.PENDING)
                .inputPath("inputs/" + id + ".json")
                .createdAt(Instant.now())
                .build();

        jobRepository.save(job);

        Optional<OptimizationJob> found = jobRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(JobType.TSP);
        assertThat(found.get().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(found.get().getInputPath()).isEqualTo("inputs/" + id + ".json");
    }

    @Test
    void savesAndFindsUsage() {
        UUID jobId = UUID.randomUUID();
        OptimizationJob job = OptimizationJob.builder()
                .id(jobId)
                .type(JobType.VRP)
                .status(JobStatus.DONE)
                .inputPath("inputs/" + jobId + ".json")
                .createdAt(Instant.now())
                .build();
        jobRepository.save(job);

        UUID usageId = UUID.randomUUID();
        Usage usage = Usage.builder()
                .id(usageId)
                .optimizationJobId(jobId)
                .ipAddress("200.1.2.3")
                .userAgent("curl/8")
                .requestedAt(Instant.now())
                .build();
        usageRepository.save(usage);

        Optional<Usage> found = usageRepository.findById(usageId);
        assertThat(found).isPresent();
        assertThat(found.get().getOptimizationJobId()).isEqualTo(jobId);
        assertThat(found.get().getIpAddress()).isEqualTo("200.1.2.3");
    }
}