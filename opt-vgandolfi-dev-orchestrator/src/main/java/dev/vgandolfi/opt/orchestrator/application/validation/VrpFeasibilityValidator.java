package dev.vgandolfi.opt.orchestrator.application.validation;

import dev.vgandolfi.opt.orchestrator.application.dto.job.request.VrpJobInput;
import dev.vgandolfi.opt.orchestrator.domain.exception.VrpInfeasibleException;
import org.springframework.stereotype.Component;

/**
 * Valida a viabilidade de um job VRP ANTES de ele ser publicado na fila,
 * espelhando exatamente o {@code _check_fleet_capacity} do worker Python
 * (opt-worker-solver/app/algorithms/vrp/vrp_solver.py):
 *
 * <ul>
 *   <li>Volume: demanda = Σ (volumeLiters ?? 0) dos clients; capacidade = Σ
 *       (maxVolumeLiters ?? 1e9) dos veículos; demanda &gt; capacidade → inviável</li>
 *   <li>Peso: idem com {@code weightKg} / {@code maxWeightKg} ?? 1e9</li>
 *   <li>Deliveries: demanda = nº de clients (1 delivery por client); capacidade =
 *       Σ (maxDeliveries ?? 1e6); demanda &gt; capacidade → inviável</li>
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
        long totalVolume = input.clients().stream()
                .mapToLong(client -> client.volumeLiters() == null ? 0L : client.volumeLiters().longValue())
                .sum();
        long totalWeight = input.clients().stream()
                .mapToLong(client -> client.weightKg() == null ? 0L : client.weightKg().longValue())
                .sum();
        long totalDeliveries = input.clients().size();

        long totalVolumeCapacity = input.vehicles().stream()
                .mapToLong(vehicle -> vehicle.maxVolumeLiters() == null
                        ? UNLIMITED_VOLUME_WEIGHT : vehicle.maxVolumeLiters().longValue())
                .sum();
        long totalWeightCapacity = input.vehicles().stream()
                .mapToLong(vehicle -> vehicle.maxWeightKg() == null
                        ? UNLIMITED_VOLUME_WEIGHT : vehicle.maxWeightKg().longValue())
                .sum();
        long totalDeliveryCapacity = input.vehicles().stream()
                .mapToLong(vehicle -> vehicle.maxDeliveries() == null
                        ? UNLIMITED_DELIVERIES : vehicle.maxDeliveries().longValue())
                .sum();

        if (totalVolume > totalVolumeCapacity) {
            throw new VrpInfeasibleException("volume",
                    "Fleet capacity insufficient: total volume " + totalVolume + "L > "
                            + totalVolumeCapacity + "L available");
        }
        if (totalWeight > totalWeightCapacity) {
            throw new VrpInfeasibleException("weight",
                    "Fleet capacity insufficient: total weight " + totalWeight + "kg > "
                            + totalWeightCapacity + "kg available");
        }
        if (totalDeliveries > totalDeliveryCapacity) {
            throw new VrpInfeasibleException("deliveries",
                    "Fleet capacity insufficient: total deliveries " + totalDeliveries + " > "
                            + totalDeliveryCapacity + " available");
        }
    }
}