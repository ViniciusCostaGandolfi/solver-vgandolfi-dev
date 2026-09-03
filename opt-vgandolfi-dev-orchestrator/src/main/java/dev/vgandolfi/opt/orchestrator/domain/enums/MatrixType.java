package dev.vgandolfi.opt.orchestrator.domain.enums;

/**
 * Tipo de matriz de distâncias usado pelos solvers.
 * EUCLIDIAN calcula distâncias em linha reta (haversine no worker);
 * STREET usa uma API de rotas reais (OSRM no worker).
 */
public enum MatrixType {
    EUCLIDIAN,
    STREET
}
