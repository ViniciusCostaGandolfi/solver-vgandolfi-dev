package dev.vgandolfi.opt.orchestrator.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RabbitProperties;
import lombok.RequiredArgsConstructor;

/**
 * Declara o exchange direto, as filas de requisição (uma por tipo de job) e a
 * fila de resultado. As filas de requisição usam DLX para replicar exatamente
 * as declarações do worker Python (declarações duplicadas com os mesmos args
 * são compatíveis no RabbitMQ).
 */
@Configuration
@RequiredArgsConstructor
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "routing.exchange";
    public static final String DLQ_EXCHANGE_NAME = "routing.exchange.dlq";

    public static final String WEBHOOK_PROCESS_QUEUE = "webhook.process.queue";
    public static final String WEBHOOK_RETRY_QUEUE = "webhook.retry.queue";

    private final RabbitProperties rabbitProperties;

    @Bean
    public DirectExchange routingExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange routingDlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue tspRequestQueue() {
        return QueueBuilder.durable("routing.tsp.request.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", "routing.tsp.request.queue.dlq")
                .build();
    }

    @Bean
    public Queue vrpRequestQueue() {
        return QueueBuilder.durable("routing.vrp.request.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", "routing.vrp.request.queue.dlq")
                .build();
    }

    @Bean
    public Queue matrixRequestQueue() {
        return QueueBuilder.durable("routing.matrix.request.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", "routing.matrix.request.queue.dlq")
                .build();
    }

    @Bean
    public Queue resultQueue() {
        return QueueBuilder.durable(rabbitProperties.resultQueue()).build();
    }

    /**
     * Fila de processamento dos retries de webhook. Tem DLX próprio para evitar
     * requeue infinito se o consumer lançar exceção (fail-open).
     */
    @Bean
    public Queue webhookProcessQueue() {
        return QueueBuilder.durable(WEBHOOK_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", WEBHOOK_PROCESS_QUEUE + ".dlq")
                .build();
    }

    /**
     * Fila de retry com TTL por mensagem: ao expirar, o DLX (routing.exchange)
     * roteia de volta para a fila de processamento com a routing key
     * {@code webhook.process.queue}.
     */
    @Bean
    public Queue webhookRetryQueue() {
        return QueueBuilder.durable(WEBHOOK_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", WEBHOOK_PROCESS_QUEUE)
                .build();
    }

    @Bean
    public Binding tspBinding(Queue tspRequestQueue, DirectExchange routingExchange) {
        return BindingBuilder.bind(tspRequestQueue).to(routingExchange).with("routing.tsp.request");
    }

    @Bean
    public Binding vrpBinding(Queue vrpRequestQueue, DirectExchange routingExchange) {
        return BindingBuilder.bind(vrpRequestQueue).to(routingExchange).with("routing.vrp.request");
    }

    @Bean
    public Binding matrixBinding(Queue matrixRequestQueue, DirectExchange routingExchange) {
        return BindingBuilder.bind(matrixRequestQueue).to(routingExchange).with("routing.matrix.request");
    }

    @Bean
    public Binding resultBinding(Queue resultQueue, DirectExchange routingExchange) {
        return BindingBuilder.bind(resultQueue).to(routingExchange).with("routing.result");
    }

    @Bean
    public Binding webhookProcessBinding(Queue webhookProcessQueue, DirectExchange routingExchange) {
        return BindingBuilder.bind(webhookProcessQueue).to(routingExchange).with(WEBHOOK_PROCESS_QUEUE);
    }
}