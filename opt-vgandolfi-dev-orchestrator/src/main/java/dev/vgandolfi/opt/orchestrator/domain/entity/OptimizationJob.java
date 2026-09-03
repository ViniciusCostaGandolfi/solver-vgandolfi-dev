package dev.vgandolfi.opt.orchestrator.domain.entity;

import dev.vgandolfi.opt.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade raiz do agregado de jobs de otimização. Encapsula as transições de
 * status como comportamento de domínio (markRunning/markCompleted/markFailed).
 */
@Entity
@Table(name = "optimization_jobs", indexes = {
        @Index(name = "idx_optimization_jobs_status", columnList = "status"),
        @Index(name = "idx_optimization_jobs_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "input_path", nullable = false, length = 500)
    private String inputPath;

    @Column(name = "output_path", length = 500)
    private String outputPath;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Marca o job como RUNNING e registra o início do processamento. */
    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /** Marca o job como DONE, registrando o output e o tempo de processamento. */
    public void markCompleted(String outputPath, Long durationMs) {
        this.status = JobStatus.DONE;
        this.outputPath = outputPath;
        this.finishedAt = Instant.now();
        this.processingTimeMs = resolveProcessingTime(durationMs);
    }

    /** Marca o job como ERROR, registrando a mensagem de erro. */
    public void markFailed(String errorMessage, Long durationMs) {
        this.status = JobStatus.ERROR;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
        this.processingTimeMs = resolveProcessingTime(durationMs);
    }

    private Long resolveProcessingTime(Long durationMs) {
        if (durationMs != null) {
            return durationMs;
        }
        if (startedAt != null) {
            return Duration.between(startedAt, finishedAt).toMillis();
        }
        return null;
    }
}