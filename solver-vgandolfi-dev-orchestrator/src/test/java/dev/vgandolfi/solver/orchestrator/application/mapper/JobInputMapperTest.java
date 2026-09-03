package dev.vgandolfi.solver.orchestrator.application.mapper;

import dev.vgandolfi.solver.orchestrator.application.dto.job.request.CoordinateInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.MatrixJobInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.TspJobInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.TspStopInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpClientInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpJobInput;
import dev.vgandolfi.solver.orchestrator.application.dto.job.request.VrpVehicleInput;
import dev.vgandolfi.solver.orchestrator.domain.enums.MatrixType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do {@link JobInputMapper}: verifica o payload JSON exato
 * (snake_case) que o worker Python consome a partir do S3 (TspRequest, VrpIn,
 * MatrixRequest em solver-vgandolfi-dev-worker/app/dtos.py).
 */
class JobInputMapperTest {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final JobInputMapper mapper = new JobInputMapper(new JsonMapper());

    @Test
    void toTspInputJsonGeneratesExactWorkerPayload() {
        TspJobInput input = new TspJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(
                        new TspStopInput("A", "Cliente A", new CoordinateInput(-23.55, -46.65)),
                        new TspStopInput("B", null, new CoordinateInput(-23.6, -46.7))),
                MatrixType.STREET);

        String json = mapper.toTspInputJson(input);

        String expected = "{"
                + "\"origin\":{\"customer_name\":\"\",\"street_name\":\"\",\"street_number\":\"\",\"city\":\"\",\"state\":\"\",\"postal_code\":\"\",\"latitude\":-23.5,\"longitude\":-46.6},"
                + "\"stops\":["
                + "{\"id\":\"A\",\"customer_name\":\"Cliente A\",\"address\":{\"customer_name\":\"Cliente A\",\"street_name\":\"\",\"street_number\":\"\",\"city\":\"\",\"state\":\"\",\"postal_code\":\"\",\"latitude\":-23.55,\"longitude\":-46.65}},"
                + "{\"id\":\"B\",\"customer_name\":null,\"address\":{\"customer_name\":\"\",\"street_name\":\"\",\"street_number\":\"\",\"city\":\"\",\"state\":\"\",\"postal_code\":\"\",\"latitude\":-23.6,\"longitude\":-46.7}}"
                + "],"
                + "\"matrix_type\":\"STREET\""
                + "}";
        assertThat(json).isEqualTo(expected);
    }

    @Test
    void toTspInputJsonDefaultsMatrixTypeToEuclidian() {
        TspJobInput input = new TspJobInput(
                new CoordinateInput(0, 0),
                List.of(new TspStopInput("A", "A", new CoordinateInput(1, 1))),
                null);

        JsonNode root = parse(mapper.toTspInputJson(input));

        assertThat(root.get("matrix_type").asText()).isEqualTo("EUCLIDIAN");
        assertThat(root.get("origin").get("latitude").asDouble()).isEqualTo(0.0);
    }

    @Test
    void toVrpInputJsonGeneratesWorkerPayload() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(-23.5, -46.6),
                List.of(
                        new VrpClientInput("c1", "Cliente 1", new CoordinateInput(-23.55, -46.65), 10.5, 2.0),
                        new VrpClientInput("c2", null, new CoordinateInput(-23.6, -46.7), null, null)),
                List.of(new VrpVehicleInput("Van 1", 5, 100.0, 200.0)),
                MatrixType.EUCLIDIAN);

        JsonNode root = parse(mapper.toVrpInputJson(input));

        assertThat(root.get("matrix_type").asText()).isEqualTo("EUCLIDIAN");

        JsonNode origin = root.get("origin");
        assertThat(origin.get("customer_name").asText()).isEmpty();
        assertThat(origin.get("street_name").asText()).isEmpty();
        assertThat(origin.get("city").asText()).isEmpty();
        assertThat(origin.get("postal_code").asText()).isEmpty();
        assertThat(origin.get("latitude").asDouble()).isEqualTo(-23.5);
        assertThat(origin.get("longitude").asDouble()).isEqualTo(-46.6);

        JsonNode clients = root.get("clients");
        assertThat(clients).hasSize(2);

        JsonNode c1 = clients.get(0);
        assertThat(c1.get("id").asText()).matches(UUID_PATTERN);
        assertThat(c1.get("customer_name").asText()).isEqualTo("Cliente 1");
        assertThat(c1.get("volume_liters").asDouble()).isEqualTo(10.5);
        assertThat(c1.get("weight_kg").asDouble()).isEqualTo(2.0);
        assertThat(c1.get("created_at").asInt()).isZero();
        assertThat(c1.get("address").get("customer_name").asText()).isEqualTo("Cliente 1");
        assertThat(c1.get("address").get("latitude").asDouble()).isEqualTo(-23.55);
        assertThat(c1.get("address").get("longitude").asDouble()).isEqualTo(-46.65);

        JsonNode c2 = clients.get(1);
        assertThat(c2.get("id").asText()).matches(UUID_PATTERN);
        assertThat(c2.get("customer_name").isNull()).isTrue();
        assertThat(c2.get("volume_liters").asDouble()).isZero();
        assertThat(c2.get("weight_kg").asDouble()).isZero();
        assertThat(c2.get("address").get("customer_name").asText()).isEmpty();

        JsonNode vehicles = root.get("vehicles");
        assertThat(vehicles).hasSize(1);
        JsonNode v1 = vehicles.get(0);
        assertThat(v1.get("id").asText()).matches(UUID_PATTERN);
        assertThat(v1.get("name").asText()).isEqualTo("Van 1");
        assertThat(v1.get("max_volume_liters").asDouble()).isEqualTo(200.0);
        assertThat(v1.get("max_weight_kg").asDouble()).isEqualTo(100.0);
        assertThat(v1.get("max_deliveries").asInt()).isEqualTo(5);
        assertThat(v1.get("min_routes").asInt()).isZero();
        assertThat(v1.get("fixed_cost").asDouble()).isZero();

        // Cada id gerado deve ser único (clients e vehicles).
        assertThat(c1.get("id").asText()).isNotEqualTo(c2.get("id").asText());
        assertThat(c1.get("id").asText()).isNotEqualTo(v1.get("id").asText());
        assertThat(c2.get("id").asText()).isNotEqualTo(v1.get("id").asText());
    }

    @Test
    void toVrpInputJsonGeneratesClientUuidWhenIdMissing() {
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(0, 0),
                List.of(new VrpClientInput(null, "Sem id", new CoordinateInput(1, 1), null, null)),
                List.of(new VrpVehicleInput("Van", null, null, null)),
                MatrixType.EUCLIDIAN);

        JsonNode root = parse(mapper.toVrpInputJson(input));

        assertThat(root.get("clients").get(0).get("id").asText()).matches(UUID_PATTERN);
        assertThat(root.get("vehicles").get(0).get("max_volume_liters").isNull()).isTrue();
        assertThat(root.get("vehicles").get(0).get("max_weight_kg").isNull()).isTrue();
        assertThat(root.get("vehicles").get(0).get("max_deliveries").isNull()).isTrue();
    }

    @Test
    void toVrpInputJsonNormalizesClientIdsToUuidIgnoringUserIds() {
        // Ids curtos enviados pelo usuário não são UUIDs e quebrariam o worker
        // (pydantic uuid_parsing → 422). O mapper deve SEMPRE gerar um UUID novo.
        VrpJobInput input = new VrpJobInput(
                new CoordinateInput(0, 0),
                List.of(
                        new VrpClientInput("c1", "Cliente 1", new CoordinateInput(1, 1), 5.0, 1.0),
                        new VrpClientInput("c2", "Cliente 2", new CoordinateInput(2, 2), 5.0, 1.0),
                        new VrpClientInput("cliente-x", "Cliente 3", new CoordinateInput(3, 3), 5.0, 1.0)),
                List.of(new VrpVehicleInput("Van 1", null, null, null)),
                MatrixType.EUCLIDIAN);

        JsonNode root = parse(mapper.toVrpInputJson(input));

        JsonNode clients = root.get("clients");
        assertThat(clients).hasSize(3);

        String[] generatedIds = new String[3];
        for (int i = 0; i < clients.size(); i++) {
            generatedIds[i] = clients.get(i).get("id").asText();
            assertThat(generatedIds[i])
                    .as("clients[%d].id deve ser um UUID válido", i)
                    .matches(UUID_PATTERN);
        }
        assertThat(generatedIds).doesNotHaveDuplicates();
        assertThat(root.get("vehicles").get(0).get("id").asText()).matches(UUID_PATTERN);

        // O resto do mapeamento permanece intacto.
        assertThat(clients.get(0).get("customer_name").asText()).isEqualTo("Cliente 1");
        assertThat(clients.get(0).get("volume_liters").asDouble()).isEqualTo(5.0);
        assertThat(clients.get(0).get("weight_kg").asDouble()).isEqualTo(1.0);
        assertThat(clients.get(0).get("created_at").asInt()).isZero();
        assertThat(clients.get(0).get("address").get("customer_name").asText()).isEqualTo("Cliente 1");
    }

    @Test
    void toMatrixInputJsonGeneratesExactWorkerPayload() {
        MatrixJobInput input = new MatrixJobInput(
                List.of(new CoordinateInput(-23.5, -46.6), new CoordinateInput(-23.55, -46.65)),
                MatrixType.STREET);

        String json = mapper.toMatrixInputJson(input);

        String expected = "{\"coordinates\":[{\"lat\":-23.5,\"lng\":-46.6},{\"lat\":-23.55,\"lng\":-46.65}],\"matrix_type\":\"STREET\"}";
        assertThat(json).isEqualTo(expected);
    }

    private JsonNode parse(String json) {
        try {
            return new JsonMapper().readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("Failed to parse JSON: " + json, ex);
        }
    }
}
