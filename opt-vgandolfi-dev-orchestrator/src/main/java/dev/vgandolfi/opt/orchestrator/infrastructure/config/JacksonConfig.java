package dev.vgandolfi.opt.orchestrator.infrastructure.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configura o conversor JSON das mensagens RabbitMQ sobre o {@link JsonMapper}
 * (Jackson 3) gerenciado pelo Spring Boot 4. O Jackson 3 serializa java.time e
 * records nativamente com nomes camelCase (contrato do worker Python).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}