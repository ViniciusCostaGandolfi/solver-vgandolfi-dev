package dev.vgandolfi.solver.orchestrator.domain.repository;

import dev.vgandolfi.solver.orchestrator.domain.entity.OptimizationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJob, UUID> {
}