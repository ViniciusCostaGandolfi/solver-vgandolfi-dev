package dev.vgandolfi.solver.orchestrator.application.dto.job.request;

import dev.vgandolfi.solver.orchestrator.domain.enums.MatrixType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Input tipado de um job de matriz de distâncias.
 *
 * @param coordinates coordenadas que formam a matriz (até 500).
 * @param matrixType  tipo de matriz de distâncias (default EUCLIDIAN).
 */
public record MatrixJobInput(
        @Size(min = 2, max = 500) @Valid List<CoordinateInput> coordinates,
        MatrixType matrixType) {

    public MatrixJobInput {
        if (matrixType == null) {
            matrixType = MatrixType.EUCLIDIAN;
        }
    }
}
