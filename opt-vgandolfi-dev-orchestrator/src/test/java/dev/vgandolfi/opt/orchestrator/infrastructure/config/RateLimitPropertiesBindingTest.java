package dev.vgandolfi.opt.orchestrator.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documenta a precedência do binding de {@link RateLimitProperties}: um
 * application-*.yml de profile que define {@code app.rate-limit.*} diretamente
 * sobrescreve o placeholder {@code ${RATE_LIMIT_*_PER_MINUTE}} do
 * application.yml base — mesmo quando a env/var de sistema está presente.
 *
 * <p>Isso é o "conflito de binding" que causa 429 em massa se a aplicação for
 * iniciada com um profile cujo yml fixe valores pequenos (ex.: profile test
 * aqui define polls=5, jobs=3, geo=3). O teste serve de guarda: se alguém
 * alterar os valores do application-test.yml, este teste quebra e alerta sobre
 * o impacto na suíte.
 */
@SpringBootTest(properties = "RATE_LIMIT_POLLS_PER_MINUTE=600")
@ActiveProfiles("test")
class RateLimitPropertiesBindingTest {

    @Autowired private RateLimitProperties properties;

    @Test
    void profileSpecificYamlTakesPrecedenceOverEnvironmentPlaceholder() {
        assertThat(properties.pollsPerMinute()).isEqualTo(5);
        assertThat(properties.jobsPerMinute()).isEqualTo(3);
        assertThat(properties.geoPerMinute()).isEqualTo(3);
    }
}