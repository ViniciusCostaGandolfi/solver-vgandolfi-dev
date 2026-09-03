package dev.vgandolfi.opt.orchestrator.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de uso por requisição (auditoria de rate-limit e origem das chamadas).
 */
@Entity
@Table(name = "usages", indexes = {
        @Index(name = "idx_usages_ip_address", columnList = "ip_address")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usage {

    @Id
    private UUID id;

    @Column(name = "optimization_job_id", nullable = false)
    private UUID optimizationJobId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
}