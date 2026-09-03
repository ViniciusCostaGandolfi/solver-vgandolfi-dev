package dev.vgandolfi.opt.orchestrator.application.dto.job.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Cliente (entrega) de um job VRP. Campos de demanda opcionais viram 0 no
 * payload do worker quando ausentes.
 *
 * @param id            identificador opcional (gerado quando ausente).
 * @param name          nome do cliente.
 * @param location      coordenada de entrega.
 * @param volumeLiters  demanda em litros.
 * @param weightKg      demanda em kg.
 */
public record VrpClientInput(
        String id,
        String name,
        @NotNull @Valid CoordinateInput location,
        Double volumeLiters,
        Double weightKg) {
}
