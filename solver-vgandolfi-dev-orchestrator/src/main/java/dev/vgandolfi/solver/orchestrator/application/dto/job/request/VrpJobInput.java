package dev.vgandolfi.solver.orchestrator.application.dto.job.request;

import dev.vgandolfi.solver.orchestrator.domain.enums.MatrixType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Input tipado de um job VRP.
 *
 * @param origin    ponto de partida (depósito).
 * @param clients   clientes a atender.
 * @param vehicles  frota disponível.
 * @param matrixType tipo de matriz de distâncias (default EUCLIDIAN).
 * @param targetRoutes nº de rotas alvo (opcional); se nulo, o solver minimiza o
 *                     nº de rotas. Quando informado, deve ser &gt;= 1 (validado
 *                     por {@code VrpFeasibilityValidator}).
 */
public record VrpJobInput(
        @NotNull @Valid CoordinateInput origin,
        @NotEmpty @Valid List<VrpClientInput> clients,
        @NotEmpty @Valid List<VrpVehicleInput> vehicles,
        MatrixType matrixType,
        Integer targetRoutes) {

    public VrpJobInput {
        if (matrixType == null) {
            matrixType = MatrixType.EUCLIDIAN;
        }
    }
}
