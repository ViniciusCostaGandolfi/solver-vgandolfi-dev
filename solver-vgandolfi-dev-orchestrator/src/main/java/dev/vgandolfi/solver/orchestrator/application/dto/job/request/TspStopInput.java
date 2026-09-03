package dev.vgandolfi.solver.orchestrator.application.dto.job.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Parada do TSP.
 *
 * @param id       identificador único da parada.
 * @param name     nome do cliente (opcional, vira {@code customer_name}).
 * @param location coordenada da parada.
 */
public record TspStopInput(
        @NotBlank String id,
        String name,
        @NotNull @Valid CoordinateInput location) {
}
