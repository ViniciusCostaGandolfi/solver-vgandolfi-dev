package dev.vgandolfi.solver.orchestrator.application.validation;

import dev.vgandolfi.solver.orchestrator.application.dto.job.request.CoordinateInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpClientInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpJobInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpVehicleInput;
import dev.vgandolfi.solver.orchestrator.domain.enums.MatrixType;
import dev.vgandolfi.solver.orchestrator.domain.exception.VrpInfeasibleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o {@link VrpFeasibilityValidator}: a mesma semântica do
 * {@code _check_fleet_capacity} do worker Python (volume, peso, deliveries),
 * com default "infinito" (1e9/1e6) para capacidades nulas.
 */
class VrpFeasibilityValidatorTest {

    private final VrpFeasibilityValidator validator = new VrpFeasibilityValidator();

    private VrpVehicleInput van(double volume, double weight, int deliveries) {
        return new VrpVehicleInput("Van", deliveries, weight, volume);
    }

    private VrpClientInput client(Double volume, Double weight) {
        return new VrpClientInput("c1", "Cliente", new CoordinateInput(-23.5, -46.6), volume, weight);
    }

    @Test
    void acceptsFeasibleJob() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(30.0, 50.0), client(40.0, 60.0)),
                List.of(van(100.0, 200.0, 5)),
                MatrixType.EUCLIDIAN);

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenTotalVolumeExceedsFleet() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(70.0, 0.0), client(60.0, 0.0)),
                List.of(van(120.0, 500.0, 10)),
                MatrixType.EUCLIDIAN);

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("volume");
                    assertThat(ex.getMessage()).isEqualTo(
                            "Fleet capacity insufficient: total volume 130L > 120L available");
                });
    }

    @Test
    void rejectsWhenTotalWeightExceedsFleet() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(0.0, 300.0), client(0.0, 200.0)),
                List.of(van(500.0, 400.0, 10)),
                MatrixType.EUCLIDIAN);

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("weight");
                    assertThat(ex.getMessage()).isEqualTo(
                            "Fleet capacity insufficient: total weight 500kg > 400kg available");
                });
    }

    @Test
    void rejectsWhenDeliveriesExceedFleet() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(10.0, 10.0), client(10.0, 10.0), client(10.0, 10.0)),
                List.of(new VrpVehicleInput("Van", 2, 100.0, 100.0)),
                MatrixType.EUCLIDIAN);

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("deliveries");
                    assertThat(ex.getMessage()).isEqualTo(
                            "Fleet capacity insufficient: total deliveries 3 > 2 available");
                });
    }

    @Test
    void nullCapacitiesAreTreatedAsUnlimited() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(1_000_000.0, 500_000.0), client(1_000_000.0, 500_000.0)),
                List.of(new VrpVehicleInput("Van", null, null, null)),
                MatrixType.EUCLIDIAN);

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }

    @Test
    void nullDemandsCountAsZero() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(null, null), client(null, null)),
                List.of(new VrpVehicleInput("Van", 5, 1.0, 1.0)),
                MatrixType.EUCLIDIAN);

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }
}