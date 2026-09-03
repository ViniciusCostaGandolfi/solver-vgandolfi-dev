package dev.vgandolfi.opt.orchestrator.domain.exception;

/**
 * Lançada quando um CEP não é encontrado (ou o upstream OpenCEP falha em
 * fail-open) — mapeada para HTTP 404 pelo GlobalExceptionHandler.
 */
public class CepNotFoundException extends RuntimeException {

    public CepNotFoundException(String cep) {
        super("CEP not found: " + cep);
    }
}