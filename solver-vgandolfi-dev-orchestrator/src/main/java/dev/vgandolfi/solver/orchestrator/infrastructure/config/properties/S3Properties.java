package dev.vgandolfi.solver.orchestrator.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(String endpoint, String accessKey, String secretKey, String bucket) {
}