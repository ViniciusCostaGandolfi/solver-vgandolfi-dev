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
 * Testa o {@link VrpFeasibilityValidator}: validação POR-CLIENTE (a frota é
 * expandida no solver), com default "infinito" (1e9/1e6) para capacidades nulas,
 * e a restrição {@code targetRoutes >= 1}.
 */
class VrpFeasibilityValidatorTest {

    private final VrpFeasibilityValidator validator = new VrpFeasibilityValidator();

    private VrpVehicleInput van(double volume, double weight, int deliveries) {
        return new VrpVehicleInput("Van", deliveries, weight, volume);
    }

    private VrpClientInput client(Double volume, Double weight) {
        return new VrpClientInput("c1", "Cliente", new CoordinateInput(-23.5, -46.6), volume, weight);
    }

    private VrpJobInput vrp(List<VrpClientInput> clients, List<VrpVehicleInput> vehicles) {
        return new VrpJobInput(
                new CoordinateInput(-23.5, -46.6), clients, vehicles, MatrixType.EUCLIDIAN, null);
    }

    @Test
    void acceptsFeasibleJob() {
        VrpJobInput input = vrp(
                List.of(client(30.0, 50.0), client(40.0, 60.0)),
                List.of(van(100.0, 200.0, 5)));

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }

    @Test
    void acceptsWhenAggregateExceedsFleetButEachClientFits() {
        // A soma (130L) excede a capacidade do único veículo (120L), mas nenhum
        // cliente isolado excede: a frota é expandida no solver → viável.
        VrpJobInput input = vrp(
                List.of(client(70.0, 0.0), client(60.0, 0.0)),
                List.of(van(120.0, 500.0, 10)));

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenSingleClientVolumeExceedsLargestVehicle() {
        VrpJobInput input = vrp(
                List.of(client(70.0, 0.0), client(130.0, 0.0)),
                List.of(van(100.0, 500.0, 10), van(120.0, 500.0, 10)));

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("volume");
                    assertThat(ex.getMessage()).isEqualTo(
                            "Fleet capacity insufficient: client volume 130L > 120L available (single vehicle)");
                });
    }

    @Test
    void rejectsWhenSingleClientWeightExceedsLargestVehicle() {
        VrpJobInput input = vrp(
                List.of(client(0.0, 300.0), client(0.0, 500.0)),
                List.of(van(500.0, 300.0, 10), van(500.0, 400.0, 10)));

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("weight");
                    assertThat(ex.getMessage()).isEqualTo(
                            "Fleet capacity insufficient: client weight 500kg > 400kg available (single vehicle)");
                });
    }

    @Test
    void rejectsWhenSingleClientDeliveryExceedsLargestVehicle() {
        VrpJobInput input = vrp(
                List.of(client(10.0, 10.0), client(10.0, 10.0)),
                List.of(new VrpVehicleInput("Van", 0, 100.0, 100.0)));

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("deliveries");
                    assertThat(ex.getMessage()).isEqualTo(
                            "Fleet capacity insufficient: client deliveries 1 > 0 available (single vehicle)");
                });
    }

    @Test
    void rejectsWhenTargetRoutesIsNotPositive() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(client(10.0, 10.0)),
                List.of(van(100.0, 100.0, 5)),
                MatrixType.EUCLIDIAN,
                0);

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOf(VrpInfeasibleException.class)
                .satisfies(ex -> {
                    assertThat(((VrpInfeasibleException) ex).field()).isEqualTo("target_routes");
                    assertThat(ex.getMessage()).isEqualTo("target_routes must be >= 1");
                });
    }

    @Test
    void acceptsWhenTargetRoutesIsNull() {
        VrpJobInput input = vrp(List.of(client(10.0, 10.0)), List.of(van(100.0, 100.0, 5)));

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }

    @Test
    void nullCapacitiesAreTreatedAsUnlimited() {
        VrpJobInput input = vrp(
                List.of(client(1_000_000.0, 500_000.0), client(1_000_000.0, 500_000.0)),
                List.of(new VrpVehicleInput("Van", null, null, null)));

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }

    @Test
    void nullDemandsCountAsZero() {
        VrpJobInput input = vrp(
                List.of(client(null, null), client(null, null)),
                List.of(new VrpVehicleInput("Van", 5, 1.0, 1.0)));

        assertThatCode(() -> validator.validate(input)).doesNotThrowAnyException();
    }
}
