package dev.vgandolfi.opt.orchestrator.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, String nominatimUrl, String opencepUrl) {
}