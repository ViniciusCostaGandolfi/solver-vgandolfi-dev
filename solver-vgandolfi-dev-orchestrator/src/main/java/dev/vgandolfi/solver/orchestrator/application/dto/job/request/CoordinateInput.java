package dev.vgandolfi.solver.orchestrator.application.dto.job.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Coordenada geográfica (WGS84). Latitudes em [-90, 90] e longitudes em
 * [-180, 180].
 */
public record CoordinateInput(
        @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {
}
