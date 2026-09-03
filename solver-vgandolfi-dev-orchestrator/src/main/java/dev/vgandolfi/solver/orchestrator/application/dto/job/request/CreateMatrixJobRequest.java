package dev.vgandolfi.solver.orchestrator.application.dto.job.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Envelope público de criação de job de matriz de distâncias. O
 * {@code webhookUrl} vive no nível do envelope, fora do input tipado.
 *
 * @param input      input tipado da matriz.
 * @param webhookUrl URL opcional para notificação assíncrona.
 */
public record CreateMatrixJobRequest(
        @NotNull @Valid MatrixJobInput input,
        @Size(max = 500) String webhookUrl) {
}
