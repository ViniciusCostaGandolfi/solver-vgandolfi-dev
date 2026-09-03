package dev.vgandolfi.opt.orchestrator.application.dto.job.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Envelope público de criação de job VRP. O {@code webhookUrl} vive no nível
 * do envelope, fora do input tipado.
 *
 * @param input      input tipado do VRP.
 * @param webhookUrl URL opcional para notificação assíncrona.
 */
public record CreateVrpJobRequest(
        @NotNull @Valid VrpJobInput input,
        @Size(max = 500) String webhookUrl) {
}
