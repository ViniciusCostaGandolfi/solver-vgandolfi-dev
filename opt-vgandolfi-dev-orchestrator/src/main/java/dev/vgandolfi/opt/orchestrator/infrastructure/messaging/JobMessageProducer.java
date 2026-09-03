package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.JobCreatedMessage;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RabbitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica mensagens de criação de job no exchange {@code routing.exchange} com
 * routing key por tipo (routing.tsp.request / routing.vrp.request / routing.matrix.request).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobMessageProducer {

    private static final Map<JobType, String> ROUTING_KEYS = Map.of(
            JobType.TSP, "routing.tsp.request",
            JobType.VRP, "routing.vrp.request",
            JobType.DISTANCE_MATRIX, "routing.matrix.request");

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties rabbitProperties;

    public void publishJob(JobCreatedMessage message) {
        String routingKey = routingKeyFor(message.jobType());
        rabbitTemplate.convertAndSend(rabbitProperties.exchange(), routingKey, message);
        log.info("job_published id={} type={} routingKey={}", message.routingJobId(), message.jobType(), routingKey);
    }

    private String routingKeyFor(JobType jobType) {
        String routingKey = ROUTING_KEYS.get(jobType);
        if (routingKey == null) {
            throw new IllegalArgumentException("No routing key configured for job type " + jobType);
        }
        return routingKey;
    }
}