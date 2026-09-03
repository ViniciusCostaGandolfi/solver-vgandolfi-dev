package dev.vgandolfi.solver.orchestrator.infrastructure.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import dev.vgandolfi.solver.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.solver.orchestrator.application.dto.geo.ReverseGeocodeResult;
import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.AppProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente Nominatim (v1.6+) via {@link RestClient}.
 *
 * <ul>
 *   <li>{@code GET {base}/search?q=..&format=json&limit=5&countrycodes=br} → array JSON</li>
 *   <li>{@code GET {base}/reverse?lat=..&lon=..&format=json} → objeto único</li>
 * </ul>
 *
 * <p>Fail-open: qualquer erro de transporte, status HTTP ou parsing é apenas
 * logado — {@code search} devolve lista vazia e {@code reverse} devolve {@code null}.</p>
 */
@Service
@Slf4j
public class RestClientNominatimClient implements NominatimClient {

    private static final String SOURCE = "nominatim";
    private static final int SEARCH_LIMIT = 5;

    private static final ParameterizedTypeReference<List<Map<String, Object>>> SEARCH_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Map<String, Object>> REVERSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public RestClientNominatimClient(RestClient.Builder builder, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.nominatimUrl()).build();
    }

    @Override
    public List<GeocodeResult> search(String address) {
        try {
            List<Map<String, Object>> items = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "json")
                            .queryParam("limit", SEARCH_LIMIT)
                            .queryParam("countrycodes", "br")
                            .queryParam("addressdetails", "1")
                            .build())
                    .retrieve()
                    .body(SEARCH_TYPE);
            if (items == null || items.isEmpty()) {
                return List.of();
            }
            return items.stream().map(this::toGeocodeResult).toList();
        } catch (Exception ex) {
            log.warn("nominatim_search_failed address={} error={}", address, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public ReverseGeocodeResult reverse(Double lat, Double lng) {
        try {
            Map<String, Object> item = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lng)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(REVERSE_TYPE);
            if (item == null || item.isEmpty()) {
                return null;
            }
            return toReverseResult(item);
        } catch (Exception ex) {
            log.warn("nominatim_reverse_failed lat={} lng={} error={}", lat, lng, ex.getMessage());
            return null;
        }
    }

    private GeocodeResult toGeocodeResult(Map<String, Object> item) {
        Map<String, Object> address = asAddressMap(item.get("address"));
        return new GeocodeResult(
                asString(item.get("display_name")),
                asString(address.get("road")),
                asString(address.get("city")),
                asString(address.get("state")),
                asString(address.get("postcode")),
                toDouble(item.get("lat")),
                toDouble(item.get("lon")),
                SOURCE);
    }

    private ReverseGeocodeResult toReverseResult(Map<String, Object> item) {
        Map<String, Object> address = asAddressMap(item.get("address"));
        return new ReverseGeocodeResult(
                asString(item.get("display_name")),
                asString(address.get("road")),
                asString(address.get("city")),
                asString(address.get("state")),
                asString(address.get("postcode")),
                toDouble(item.get("lat")),
                toDouble(item.get("lon")),
                SOURCE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asAddressMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Nominatim devolve lat/lon como string; converte de forma defensiva e
     * tolerante a vírgula decimal, retornando {@code null} quando inválido.
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.toString().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}