package dev.vgandolfi.opt.orchestrator.infrastructure.config.properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AppProperties.class,
        RabbitProperties.class,
        S3Properties.class,
        RateLimitProperties.class,
        WebhookProperties.class
})
public class PropertiesConfig {
}