package dev.vgandolfi.solver.orchestrator.application.service;

import dev.vgandolfi.solver.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.solver.orchestrator.application.dto.geo.ReverseGeocodeResult;
import dev.vgandolfi.solver.orchestrator.domain.exception.CepNotFoundException;
import dev.vgandolfi.solver.orchestrator.infrastructure.client.NominatimClient;
import dev.vgandolfi.solver.orchestrator.infrastructure.client.OpenCepClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Caso de uso de geocoding: apenas orquestra e loga, delegando o acesso ao
 * Nominatim (forward/reverse) e ao OpenCEP (CEP) — ambos fail-open.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    private final NominatimClient nominatimClient;
    private final OpenCepClient openCepClient;

    public List<GeocodeResult> geocode(String address) {
        log.info("geo_search address={}", address);
        return nominatimClient.search(address);
    }

    public ReverseGeocodeResult reverse(Double lat, Double lng) {
        log.info("geo_reverse lat={} lng={}", lat, lng);
        return nominatimClient.reverse(lat, lng);
    }

    /**
     * Consulta um CEP. O cliente é fail-open (devolve vazio em erro/não
     * encontrado); aqui convertemos "vazio" em {@link CepNotFoundException},
     * que o handler global mapeia para 404.
     */
    public GeocodeResult lookupCep(String cep) {
        log.info("geo_cep cep={}", cep);
        return openCepClient.lookupCep(cep).orElseThrow(() -> new CepNotFoundException(cep));
    }
}