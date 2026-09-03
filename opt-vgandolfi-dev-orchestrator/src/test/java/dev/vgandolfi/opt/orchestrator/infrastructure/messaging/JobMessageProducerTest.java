package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.JobCreatedMessage;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RabbitProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobMessageProducerTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private final RabbitProperties properties = new RabbitProperties("routing.exchange", "routing.result.queue");

    @Test
    void publishesTspJobOnTspRoutingKey() {
        JobMessageProducer producer = new JobMessageProducer(rabbitTemplate, properties);
        JobCreatedMessage message = new JobCreatedMessage(UUID.randomUUID(), JobType.TSP, "inputs/x.json",
                UUID.randomUUID(), null);

        producer.publishJob(message);

        verify(rabbitTemplate).convertAndSend("routing.exchange", "routing.tsp.request", message);
    }

    @Test
    void publishesMatrixJobOnMatrixRoutingKey() {
        JobMessageProducer producer = new JobMessageProducer(rabbitTemplate, properties);
        JobCreatedMessage message = new JobCreatedMessage(UUID.randomUUID(), JobType.DISTANCE_MATRIX,
                "inputs/x.json", UUID.randomUUID(), "https://hooks.example.com/cb");

        producer.publishJob(message);

        verify(rabbitTemplate).convertAndSend("routing.exchange", "routing.matrix.request", message);
    }
}