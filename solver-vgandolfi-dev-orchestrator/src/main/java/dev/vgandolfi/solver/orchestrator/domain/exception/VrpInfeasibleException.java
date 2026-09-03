package dev.vgandolfi.solver.orchestrator.domain.exception;

/**
 * Lançada quando um job VRP é comprovadamente inviável (frota não comporta as
 * demandas) — mapeada para HTTP 422 pelo GlobalExceptionHandler, antes de o
 * job ser publicado na fila/processado pelo worker.
 */
public class VrpInfeasibleException extends RuntimeException {

    private final String field;

    /**
     * @param field   dimensão que falhou (volume | weight | deliveries)
     * @param message mensagem específica da dimensão (mesma semântica do worker
     *                Python, {@code _check_fleet_capacity})
     */
    public VrpInfeasibleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}