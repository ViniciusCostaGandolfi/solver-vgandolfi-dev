package dev.vgandolfi.opt.orchestrator.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int jobsPerMinute, int pollsPerMinute, int geoPerMinute) {
}