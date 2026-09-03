package dev.vgandolfi.opt.orchestrator.application.dto.job.request;

import dev.vgandolfi.opt.orchestrator.domain.enums.MatrixType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Input tipado de um job TSP.
 *
 * @param origin    ponto de partida do roteiro.
 * @param stops     paradas a visitar (até 100).
 * @param matrixType tipo de matriz de distâncias (default EUCLIDIAN).
 */
public record TspJobInput(
        @NotNull @Valid CoordinateInput origin,
        @NotEmpty @Size(max = 100) @Valid List<TspStopInput> stops,
        MatrixType matrixType) {

    public TspJobInput {
        if (matrixType == null) {
            matrixType = MatrixType.EUCLIDIAN;
        }
    }
}
