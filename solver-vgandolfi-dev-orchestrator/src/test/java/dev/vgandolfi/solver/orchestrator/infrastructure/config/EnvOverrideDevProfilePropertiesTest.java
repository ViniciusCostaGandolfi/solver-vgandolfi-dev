package dev.vgandolfi.solver.orchestrator.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.RateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova a precedência correta no profile {@code dev}: com a env presente
 * (simulada aqui por properties inline com nome de variável de ambiente), a
 * env SOBRESCREVE os defaults do yml. É o oposto do footgun documentado em
 * {@link RateLimitPropertiesBindingTest} (profile test fixa valores e ignora a
 * env) — aqui o profile dev usa apenas placeholders, então a env tem a palavra final.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:envoverride;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "DB_USER=custom-db-user",
        "DB_PASSWORD=custom-db-pass",
        "RATE_LIMIT_POLLS_PER_MINUTE=999"
})
@ActiveProfiles("dev")
class EnvOverrideDevProfilePropertiesTest {

    @Autowired private Environment env;
    @Autowired private RateLimitProperties rateLimitProperties;

    @Test
    void environmentVariablesOverrideProfilePlaceholders() {
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("custom-db-user");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("custom-db-pass");
        assertThat(rateLimitProperties.pollsPerMinute()).isEqualTo(999);
    }
}