package dev.vgandolfi.solver.orchestrator.infrastructure.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Envia o payload do webhook uma única vez (fail-open): devolve {@code true}
 * quando a resposta é 2xx e {@code false} em qualquer erro de transporte,
 * status HTTP ou timeout — sem lançar exceção para o chamador.
 */
@Component
@Slf4j
public class WebhookDispatcher {

    private final RestClient restClient;

    public WebhookDispatcher(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public boolean dispatch(String webhookUrl, Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.warn("webhook_dispatch_failed url={} error={}", webhookUrl, ex.getMessage());
            return false;
        }
    }
}