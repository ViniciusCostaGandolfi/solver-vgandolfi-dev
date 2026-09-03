package dev.vgandolfi.opt.orchestrator.infrastructure.client;

import dev.vgandolfi.opt.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.opt.orchestrator.application.dto.geo.ReverseGeocodeResult;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.AppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testa o mapeamento das respostas do Nominatim (JSON) para os DTOs e o
 * comportamento fail-open do {@link RestClientNominatimClient}.
 */
class RestClientNominatimClientTest {

    private static final String SEARCH_JSON = """
            [
              {
                "place_id": 1,
                "display_name": "Rua Augusta, Consolacao, Sao Paulo, SP, 01310-100, Brasil",
                "lat": "-23.5521",
                "lon": "-46.6542",
                "address": {
                  "road": "Rua Augusta",
                  "city": "Sao Paulo",
                  "state": "SP",
                  "postcode": "01310-100"
                }
              },
              {
                "place_id": 2,
                "display_name": "Rua Augusta, Jardins, Sao Paulo, SP, 01412-000, Brasil",
                "lat": "-23.5601",
                "lon": "-46.6650",
                "address": {
                  "road": "Rua Augusta",
                  "city": "Sao Paulo",
                  "state": "SP",
                  "postcode": "01412-000"
                }
              }
            ]
            """;

    private static final String REVERSE_JSON = """
            {
              "place_id": 3,
              "display_name": "Avenida Paulista, Bela Vista, Sao Paulo, SP, 01310-100, Brasil",
              "lat": "-23.5614",
              "lon": "-46.6559",
              "address": {
                "road": "Avenida Paulista",
                "city": "Sao Paulo",
                "state": "SP",
                "postcode": "01310-100"
              }
            }
            """;

    private MockRestServiceServer server;
    private RestClientNominatimClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientNominatimClient(builder,
                new AppProperties("http://localhost:8080", "http://localhost:18080", "http://localhost:18081"));
    }

    @Test
    void searchMapsNominatimArrayToGeocodeResults() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/search");
                    assertThat(request.getURI().getQuery()).contains("q=rua");
                    assertThat(request.getURI().getQuery()).contains("format=json");
                    assertThat(request.getURI().getQuery()).contains("limit=5");
                    assertThat(request.getURI().getQuery()).contains("countrycodes=br");
                })
                .andRespond(withSuccess(SEARCH_JSON, MediaType.APPLICATION_JSON));

        List<GeocodeResult> results = client.search("rua augusta");

        assertThat(results).hasSize(2);
        GeocodeResult first = results.get(0);
        assertThat(first.formattedAddress())
                .isEqualTo("Rua Augusta, Consolacao, Sao Paulo, SP, 01310-100, Brasil");
        assertThat(first.streetName()).isEqualTo("Rua Augusta");
        assertThat(first.city()).isEqualTo("Sao Paulo");
        assertThat(first.state()).isEqualTo("SP");
        assertThat(first.postalCode()).isEqualTo("01310-100");
        assertThat(first.latitude()).isEqualTo(-23.5521);
        assertThat(first.longitude()).isEqualTo(-46.6542);
        assertThat(first.source()).isEqualTo("nominatim");
        server.verify();
    }

    @Test
    void searchReturnsEmptyListWhenNominatimReturnsNoResults() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/search");
                    assertThat(request.getURI().getQuery()).contains("q=zona");
                })
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.search("zona inexistente")).isEmpty();
        server.verify();
    }

    @Test
    void searchFailsOpenWhenNominatimReturnsError() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/search"))
                .andRespond(withServerError());

        assertThat(client.search("rua augusta")).isEmpty();
        server.verify();
    }

    @Test
    void reverseMapsNominatimObjectToReverseGeocodeResult() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/reverse");
                    assertThat(request.getURI().getQuery()).contains("lat=-23.5614");
                    assertThat(request.getURI().getQuery()).contains("lon=-46.6559");
                    assertThat(request.getURI().getQuery()).contains("format=json");
                })
                .andRespond(withSuccess(REVERSE_JSON, MediaType.APPLICATION_JSON));

        ReverseGeocodeResult result = client.reverse(-23.5614, -46.6559);

        assertThat(result).isNotNull();
        assertThat(result.formattedAddress())
                .isEqualTo("Avenida Paulista, Bela Vista, Sao Paulo, SP, 01310-100, Brasil");
        assertThat(result.streetName()).isEqualTo("Avenida Paulista");
        assertThat(result.city()).isEqualTo("Sao Paulo");
        assertThat(result.state()).isEqualTo("SP");
        assertThat(result.postalCode()).isEqualTo("01310-100");
        assertThat(result.latitude()).isEqualTo(-23.5614);
        assertThat(result.longitude()).isEqualTo(-46.6559);
        assertThat(result.source()).isEqualTo("nominatim");
        server.verify();
    }

    @Test
    void searchToleratesMissingAddressAndInvalidCoordinates() {
        String json = """
                [
                  {
                    "display_name": "Local sem address",
                    "lat": "abc",
                    "lon": "def"
                  }
                ]
                """;
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/search"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<GeocodeResult> results = client.search("local");

        assertThat(results).hasSize(1);
        GeocodeResult result = results.get(0);
        assertThat(result.formattedAddress()).isEqualTo("Local sem address");
        assertThat(result.streetName()).isNull();
        assertThat(result.city()).isNull();
        assertThat(result.state()).isNull();
        assertThat(result.postalCode()).isNull();
        assertThat(result.latitude()).isNull();
        assertThat(result.longitude()).isNull();
        assertThat(result.source()).isEqualTo("nominatim");
        server.verify();
    }

    @Test
    void reverseReturnsNullWhenNominatimReturnsError() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/reverse"))
                .andRespond(withServerError());

        assertThat(client.reverse(-23.5614, -46.6559)).isNull();
        server.verify();
    }
}