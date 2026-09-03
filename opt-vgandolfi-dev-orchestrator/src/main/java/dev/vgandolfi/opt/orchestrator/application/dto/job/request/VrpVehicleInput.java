package dev.vgandolfi.opt.orchestrator.application.dto.job.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Veículo disponível em um job VRP. Capacidades opcionais são enviadas ao
 * worker como null (campos opcionais no modelo Python).
 *
 * @param name            nome do veículo.
 * @param maxDeliveries   limite de entregas por rota.
 * @param maxWeightKg     capacidade de peso.
 * @param maxVolumeLiters capacidade de volume.
 */
public record VrpVehicleInput(
        @NotBlank String name,
        Integer maxDeliveries,
        Double maxWeightKg,
        Double maxVolumeLiters) {
}
