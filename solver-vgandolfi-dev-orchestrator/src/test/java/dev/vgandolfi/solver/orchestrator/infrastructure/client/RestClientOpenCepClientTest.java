package dev.vgandolfi.solver.orchestrator.infrastructure.client;

import dev.vgandolfi.solver.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.AppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testa o mapeamento das respostas do OpenCEP (formato ViaCEP-like) para
 * {@link GeocodeResult} e o fail-open do {@link RestClientOpenCepClient}.
 */
class RestClientOpenCepClientTest {

    private static final String CEP_JSON = """
            {
              "cep": "01001-000",
              "logradouro": "Praça da Sé",
              "complemento": "lado ímpar",
              "bairro": "Sé",
              "localidade": "São Paulo",
              "uf": "SP"
            }
            """;

    private MockRestServiceServer server;
    private RestClientOpenCepClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientOpenCepClient(builder,
                new AppProperties("http://localhost:8080", "http://localhost:18080", "http://localhost:18082"));
    }

    @Test
    void lookupCepMapsViaCepLikeResponse() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1/01001000.json");
                })
                .andRespond(withSuccess(CEP_JSON, MediaType.APPLICATION_JSON));

        Optional<GeocodeResult> result = client.lookupCep("01001000");

        assertThat(result).isPresent();
        GeocodeResult cep = result.get();
        assertThat(cep.formattedAddress()).isEqualTo("Praça da Sé, Sé, São Paulo, SP, 01001-000");
        assertThat(cep.streetName()).isEqualTo("Praça da Sé");
        assertThat(cep.city()).isEqualTo("São Paulo");
        assertThat(cep.state()).isEqualTo("SP");
        assertThat(cep.postalCode()).isEqualTo("01001-000");
        assertThat(cep.latitude()).isNull();
        assertThat(cep.longitude()).isNull();
        assertThat(cep.source()).isEqualTo("opencep");
        server.verify();
    }

    @Test
    void lookupCepWithHyphenNormalizesToEightDigits() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/01001000.json"))
                .andRespond(withSuccess(CEP_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.lookupCep("01001-000")).isPresent();
        server.verify();
    }

    @Test
    void lookupCepReturnsEmptyOn404() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/99999999.json"))
                .andRespond(withResourceNotFound());

        assertThat(client.lookupCep("99999-999")).isEmpty();
        server.verify();
    }

    @Test
    void lookupCepFailsOpenOnServerError() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/01001000.json"))
                .andRespond(withServerError());

        assertThat(client.lookupCep("01001000")).isEmpty();
        server.verify();
    }

    @Test
    void lookupCepFailsOpenOnMalformedJson() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/01001000.json"))
                .andRespond(withSuccess("{not valid json", MediaType.APPLICATION_JSON));

        assertThat(client.lookupCep("01001000")).isEmpty();
        server.verify();
    }

    @Test
    void lookupCepReturnsEmptyOnErrorBody() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v1/99999999.json"))
                .andRespond(withSuccess("{\"erro\": true}", MediaType.APPLICATION_JSON));

        assertThat(client.lookupCep("99999-999")).isEmpty();
        server.verify();
    }

    @Test
    void lookupCepReturnsEmptyForInvalidLengthWithoutHttpCall() {
        assertThat(client.lookupCep("123")).isEmpty();
        assertThat(client.lookupCep("abcdefgh")).isEmpty();
        server.verify();
    }
}