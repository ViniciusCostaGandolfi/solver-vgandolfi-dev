package dev.vgandolfi.opt.orchestrator.domain.repository;

import dev.vgandolfi.opt.orchestrator.domain.entity.Usage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsageRepository extends JpaRepository<Usage, UUID> {
}