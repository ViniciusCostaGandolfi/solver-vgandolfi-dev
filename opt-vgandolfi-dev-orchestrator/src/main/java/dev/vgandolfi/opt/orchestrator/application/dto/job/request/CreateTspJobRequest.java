package dev.vgandolfi.opt.orchestrator.application.dto.job.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Envelope público de criação de job TSP. O {@code webhookUrl} vive no nível
 * do envelope, fora do input tipado.
 *
 * @param input      input tipado do TSP.
 * @param webhookUrl URL opcional para notificação assíncrona.
 */
public record CreateTspJobRequest(
        @NotNull @Valid TspJobInput input,
        @Size(max = 500) String webhookUrl) {
}
