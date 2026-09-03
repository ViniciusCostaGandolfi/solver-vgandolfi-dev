package dev.vgandolfi.opt.orchestrator.application.mapper;

import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CoordinateInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.MatrixJobInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.TspJobInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.TspStopInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.VrpClientInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.VrpJobInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.VrpVehicleInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Converte os inputs tipados (application DTOs) no payload JSON snake_case que
 * o worker Python consome a partir do S3 (models em {@code app/dtos.py}:
 * TspRequest, VrpIn, MatrixRequest).
 *
 * <p>O {@link Address} gerado preenche apenas latitude/longitude com valores
 * reais; os demais campos exigidos pelo worker ({@code street_name},
 * {@code street_number}, {@code city}, {@code state}, {@code postal_code}) são
 * enviados como string vazia. Campos com default no worker
 * ({@code complement}, {@code neighborhood}, {@code items_description},
 * {@code has_public}, ...) são omitidos.
 */
@Component
@RequiredArgsConstructor
public class JobInputMapper {

    private final ObjectMapper objectMapper;

    public String toTspInputJson(TspJobInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origin", toAddress(input.origin(), ""));
        payload.put("stops", input.stops().stream().map(this::toStop).toList());
        payload.put("matrix_type", input.matrixType().name());
        return serialize(payload);
    }

    public String toVrpInputJson(VrpJobInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origin", toAddress(input.origin(), ""));
        payload.put("clients", input.clients().stream().map(this::toClient).toList());
        payload.put("vehicles", input.vehicles().stream().map(this::toVehicle).toList());
        payload.put("matrix_type", input.matrixType().name());
        return serialize(payload);
    }

    public String toMatrixInputJson(MatrixJobInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordinates", input.coordinates().stream().map(this::toCoordinate).toList());
        payload.put("matrix_type", input.matrixType().name());
        return serialize(payload);
    }

    private Map<String, Object> toStop(TspStopInput stop) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", stop.id());
        map.put("customer_name", stop.name());
        map.put("address", toAddress(stop.location(), nullToEmpty(stop.name())));
        return map;
    }

    private Map<String, Object> toClient(VrpClientInput client) {
        Map<String, Object> map = new LinkedHashMap<>();
        // O worker (pydantic) exige clients[].id como UUID válido; o id enviado
        // pelo usuário é apenas referência externa e nunca deve vazar para o
        // payload — gera-se um UUID novo, como já é feito para vehicles[].id.
        map.put("id", UUID.randomUUID().toString());
        map.put("customer_name", client.name());
        map.put("volume_liters", client.volumeLiters() != null ? client.volumeLiters() : 0.0);
        map.put("weight_kg", client.weightKg() != null ? client.weightKg() : 0.0);
        map.put("created_at", 0);
        map.put("address", toAddress(client.location(), nullToEmpty(client.name())));
        return map;
    }

    private Map<String, Object> toVehicle(VrpVehicleInput vehicle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", UUID.randomUUID().toString());
        map.put("name", vehicle.name());
        map.put("max_volume_liters", vehicle.maxVolumeLiters());
        map.put("max_weight_kg", vehicle.maxWeightKg());
        map.put("max_deliveries", vehicle.maxDeliveries());
        map.put("min_routes", 0);
        map.put("fixed_cost", 0.0);
        return map;
    }

    private Map<String, Object> toCoordinate(CoordinateInput coordinate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lat", coordinate.lat());
        map.put("lng", coordinate.lng());
        return map;
    }

    private Map<String, Object> toAddress(CoordinateInput location, String customerName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("customer_name", customerName);
        map.put("street_name", "");
        map.put("street_number", "");
        map.put("city", "");
        map.put("state", "");
        map.put("postal_code", "");
        map.put("latitude", location.lat());
        map.put("longitude", location.lng());
        return map;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Failed to serialize job input", ex);
        }
    }
}
