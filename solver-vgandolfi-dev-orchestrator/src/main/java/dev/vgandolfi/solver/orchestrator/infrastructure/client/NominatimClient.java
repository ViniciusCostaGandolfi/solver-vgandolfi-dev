package dev.vgandolfi.solver.orchestrator.infrastructure.client;

import dev.vgandolfi.solver.orchestrator.application.dto.geo.GeocodeResult;
import dev.vgandolfi.solver.orchestrator.application.dto.geo.ReverseGeocodeResult;

import java.util.List;

/**
 * Porta de acesso ao geocoding Nominatim (forward e reverse).
 * As implementações devem ser fail-open: nunca lançam exceção para o chamador.
 */
public interface NominatimClient {

    /**
     * Busca endereços a partir de um texto (forward geocoding), limitado ao Brasil.
     *
     * @param address texto livre de endereço
     * @return lista de resultados (vazia em caso de erro ou nenhum resultado)
     */
    List<GeocodeResult> search(String address);

    /**
     * Resolve um local a partir de coordenadas (reverse geocoding).
     *
     * @param lat latitude
     * @param lng longitude
     * @return resultado ou {@code null} quando nada é encontrado ou em caso de erro
     */
    ReverseGeocodeResult reverse(Double lat, Double lng);
}