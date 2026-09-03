package dev.vgandolfi.opt.orchestrator.infrastructure.client;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import dev.vgandolfi.opt.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.AppProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente OpenCEP (formato ViaCEP-like) via {@link RestClient}.
 *
 * <ul>
 *   <li>{@code GET {base}/v1/{cep}.json} → objeto JSON com {@code cep},
 *       {@code logradouro}, {@code complemento}, {@code bairro},
 *       {@code localidade}, {@code uf}.</li>
 * </ul>
 *
 * <p>Fail-open: erro de transporte, status HTTP não-2xx, JSON malformado ou
 * {@code {"erro": true}} → {@link Optional#empty()} com log.warn. O OpenCEP não
 * devolve latitude/longitude, então o {@link GeocodeResult} sai com esses
 * campos {@code null} e {@code source="opencep"}.</p>
 */
@Service
@Slf4j
public class RestClientOpenCepClient implements OpenCepClient {

    private static final String SOURCE = "opencep";

    private static final ParameterizedTypeReference<Map<String, Object>> CEP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public RestClientOpenCepClient(RestClient.Builder builder, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.opencepUrl()).build();
    }

    @Override
    public Optional<GeocodeResult> lookupCep(String cep) {
        String normalized = normalize(cep);
        if (normalized.length() != 8) {
            log.warn("opencep_invalid_cep cep={}", cep);
            return Optional.empty();
        }
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/" + normalized + ".json").build())
                    .retrieve()
                    .body(CEP_TYPE);
            if (body == null || body.isEmpty() || isErrorBody(body)) {
                return Optional.empty();
            }
            return Optional.of(toGeocodeResult(body));
        } catch (Exception ex) {
            log.warn("opencep_lookup_failed cep={} error={}", cep, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Remove tudo que não for dígito: "01001-000" → "01001000". */
    private String normalize(String cep) {
        if (cep == null) {
            return "";
        }
        return cep.replaceAll("\\D", "");
    }

    /** Respostas ViaCEP-like de CEP inexistente chegam como {@code {"erro": true}}. */
    private boolean isErrorBody(Map<String, Object> body) {
        Object erro = body.get("erro");
        return Boolean.TRUE.equals(erro) || "true".equalsIgnoreCase(String.valueOf(erro));
    }

    private GeocodeResult toGeocodeResult(Map<String, Object> body) {
        String logradouro = asString(body.get("logradouro"));
        String bairro = asString(body.get("bairro"));
        String localidade = asString(body.get("localidade"));
        String uf = asString(body.get("uf"));
        String cep = asString(body.get("cep"));
        return new GeocodeResult(
                joinParts(logradouro, bairro, localidade, uf, cep),
                logradouro,
                localidade,
                uf,
                cep,
                null,
                null,
                SOURCE);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String joinParts(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }
}