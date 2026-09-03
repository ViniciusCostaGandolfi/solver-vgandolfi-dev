package dev.vgandolfi.opt.orchestrator.domain.repository;

import dev.vgandolfi.opt.orchestrator.domain.entity.OptimizationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJob, UUID> {
}