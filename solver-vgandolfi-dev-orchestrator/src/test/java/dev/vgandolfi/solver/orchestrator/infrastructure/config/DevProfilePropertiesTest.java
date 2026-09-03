package dev.vgandolfi.solver.orchestrator.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.AppProperties;
import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.RateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que o profile {@code dev} herda o base e NÃO esconde as env vars:
 * sem env, as credenciais resolvem para os defaults locais; o overlay só adiciona
 * logging DEBUG. Isso garante que `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`
 * sobe contra o stack local e que, dentro do docker-compose, as env vars do
 * serviço api continuam tendo precedência (diferente do footgun do profile test,
 * que fixa valores e ignora a env — documentado em RateLimitPropertiesBindingTest).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:devprofile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("dev")
class DevProfilePropertiesTest {

    @Autowired private Environment env;
    @Autowired private AppProperties appProperties;
    @Autowired private RateLimitProperties rateLimitProperties;

    @Test
    void devProfileResolvesLocalDefaults() {
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("solver-vgandolfi-dev");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("solver-vgandolfi-dev");

        assertThat(env.getProperty("spring.rabbitmq.host")).isEqualTo("localhost");
        assertThat(env.getProperty("spring.rabbitmq.username")).isEqualTo("solver-vgandolfi-dev");
        assertThat(env.getProperty("spring.rabbitmq.password")).isEqualTo("solver-vgandolfi-dev");

        assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("localhost");
        assertThat(env.getProperty("app.s3.endpoint")).isEqualTo("http://localhost:9000");
        assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");

        assertThat(rateLimitProperties.jobsPerMinute()).isEqualTo(10);
        assertThat(rateLimitProperties.pollsPerMinute()).isEqualTo(600);
        assertThat(rateLimitProperties.geoPerMinute()).isEqualTo(30);
    }

    @Test
    void devProfileEnablesVerboseLogging() {
        assertThat(env.getProperty("logging.level.dev.vgandolfi.solver")).isEqualTo("DEBUG");
    }
}