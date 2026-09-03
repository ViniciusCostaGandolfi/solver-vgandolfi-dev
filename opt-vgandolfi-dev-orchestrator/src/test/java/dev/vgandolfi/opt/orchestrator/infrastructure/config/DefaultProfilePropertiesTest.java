package dev.vgandolfi.opt.orchestrator.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.AppProperties;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RateLimitProperties;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.WebhookProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que o profile DEFAULT (sem env vars) resolve as properties para os
 * defaults do stack local do docker-compose: Postgres/Rabbit com usuário
 * {@code opt-vgandolfi-dev}, Redis/S3/MinIO em localhost e rate-limit 10/600/30.
 * O datasource é sobrescrito para H2 apenas para o contexto de teste subir sem
 * depender de banco real; as demais properties são as resolvidas pelos ymls.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:defaultprofile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class DefaultProfilePropertiesTest {

    @Autowired private Environment env;
    @Autowired private AppProperties appProperties;
    @Autowired private RateLimitProperties rateLimitProperties;
    @Autowired private WebhookProperties webhookProperties;

    @Test
    void resolvesLocalStackDefaults() {
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("opt-vgandolfi-dev");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("opt-vgandolfi-dev");

        assertThat(env.getProperty("spring.rabbitmq.host")).isEqualTo("localhost");
        assertThat(env.getProperty("spring.rabbitmq.port")).isEqualTo("5672");
        assertThat(env.getProperty("spring.rabbitmq.username")).isEqualTo("opt-vgandolfi-dev");
        assertThat(env.getProperty("spring.rabbitmq.password")).isEqualTo("opt-vgandolfi-dev");

        assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("localhost");
        assertThat(env.getProperty("spring.data.redis.port")).isEqualTo("6379");

        assertThat(env.getProperty("app.s3.endpoint")).isEqualTo("http://localhost:9000");
        assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");

        assertThat(rateLimitProperties.jobsPerMinute()).isEqualTo(10);
        assertThat(rateLimitProperties.pollsPerMinute()).isEqualTo(600);
        assertThat(rateLimitProperties.geoPerMinute()).isEqualTo(30);

        // Binding de "30s,5m,1h,24h" → List<Duration> e max-attempts.
        assertThat(webhookProperties.retryDelays())
                .containsExactly(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofDays(1));
        assertThat(webhookProperties.maxAttempts()).isEqualTo(5);
    }
}