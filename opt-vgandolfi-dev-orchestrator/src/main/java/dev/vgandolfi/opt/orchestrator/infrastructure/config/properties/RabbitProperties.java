package dev.vgandolfi.opt.orchestrator.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitProperties(String exchange, String resultQueue) {
}