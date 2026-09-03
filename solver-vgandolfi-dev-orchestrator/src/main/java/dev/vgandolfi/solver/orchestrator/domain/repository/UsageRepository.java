package dev.vgandolfi.solver.orchestrator.domain.repository;

import dev.vgandolfi.solver.orchestrator.domain.entity.Usage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsageRepository extends JpaRepository<Usage, UUID> {
}