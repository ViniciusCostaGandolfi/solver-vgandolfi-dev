package dev.vgandolfi.solver.orchestrator.infrastructure.client;

import dev.vgandolfi.solver.orchestrator.application.dto.geo.GeocodeResult;

import java.util.Optional;

/**
 * Porta de consulta de CEP (OpenCEP, formato ViaCEP-like). Fail-open: nunca
 * lança exceção para o chamador — devolve {@link Optional#empty()} em erro de
 * transporte, status HTTP não-2xx, JSON malformado ou CEP não encontrado.
 */
public interface OpenCepClient {

    /**
     * Consulta um CEP, normalizando 8 dígitos ou formato "00000-000".
     *
     * @param cep CEP com 8 dígitos ou com hífen
     * @return resultado normalizado em {@link GeocodeResult} (lat/lng sempre
     * {@code null}, pois o OpenCEP não devolve coordenadas) ou vazio
     */
    Optional<GeocodeResult> lookupCep(String cep);
}