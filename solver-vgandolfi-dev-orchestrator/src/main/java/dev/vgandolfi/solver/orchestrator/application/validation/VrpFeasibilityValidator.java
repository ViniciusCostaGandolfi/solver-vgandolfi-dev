package dev.vgandolfi.solver.orchestrator.application.validation;

import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpClientInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpJobInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpVehicleInput;
import dev.vgandolfi.solver.orchestrator.domain.exception.VrpInfeasibleException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Valida a viabilidade de um job VRP ANTES de ele ser publicado na fila.
 *
 * <p>A frota agora é EXPANDIDA no solver (o número de rotas é derivado das
 * demandas), então a soma total das capacidades não é mais um limite: a única
 * impossibilidade real é um único cliente cuja demanda não cabe em NENHUM
 * veículo. Assim, a validação é POR-CLIENTE:</p>
 *
 * <ul>
 *   <li>{@code targetRoutes}: se informado, deve ser &gt;= 1</li>
 *   <li>Volume: para cada client, {@code volumeLiters ?? 0} &gt;
 *       maior {@code maxVolumeLiters} entre os veículos (ou 1e9 se todos nulos)
 *       → inviável</li>
 *   <li>Peso: idem com {@code weightKg} / {@code maxWeightKg} (ou 1e9)</li>
 *   <li>Deliveries: cada client representa 1 delivery; {@code 1} &gt;
 *       maior {@code maxDeliveries} (ou 1e6) → inviável</li>
 * </ul>
 *
 * <p>O default "infinito" (1e9/1e6) e o truncamento para inteiro replicam o
 * comportamento do worker (Python {@code int(... or 1e9)}).</p>
 */
@Component
public class VrpFeasibilityValidator {

    private static final long UNLIMITED_VOLUME_WEIGHT = 1_000_000_000L; // 1e9
    private static final long UNLIMITED_DELIVERIES = 1_000_000L;        // 1e6

    public void validate(VrpJobInput input) {
        if (input.targetRoutes() != null && input.targetRoutes() < 1) {
            throw new VrpInfeasibleException("target_routes", "target_routes must be >= 1");
        }

        long maxVolume = input.vehicles().stream()
                .map(VrpVehicleInput::maxVolumeLiters)
                .filter(Objects::nonNull)
                .mapToLong(Double::longValue)
                .max()
                .orElse(UNLIMITED_VOLUME_WEIGHT);
        long maxWeight = input.vehicles().stream()
                .map(VrpVehicleInput::maxWeightKg)
                .filter(Objects::nonNull)
                .mapToLong(Double::longValue)
                .max()
                .orElse(UNLIMITED_VOLUME_WEIGHT);
        long maxDeliveries = input.vehicles().stream()
                .map(VrpVehicleInput::maxDeliveries)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .max()
                .orElse(UNLIMITED_DELIVERIES);

        for (VrpClientInput client : input.clients()) {
            long volume = client.volumeLiters() == null ? 0L : client.volumeLiters().longValue();
            long weight = client.weightKg() == null ? 0L : client.weightKg().longValue();

            if (volume > maxVolume) {
                throw new VrpInfeasibleException("volume",
                        "Fleet capacity insufficient: client volume " + volume + "L > "
                                + maxVolume + "L available (single vehicle)");
            }
            if (weight > maxWeight) {
                throw new VrpInfeasibleException("weight",
                        "Fleet capacity insufficient: client weight " + weight + "kg > "
                                + maxWeight + "kg available (single vehicle)");
            }
            if (1L > maxDeliveries) {
                throw new VrpInfeasibleException("deliveries",
                        "Fleet capacity insufficient: client deliveries 1 > "
                                + maxDeliveries + " available (single vehicle)");
            }
        }
    }
}
