package dev.vgandolfi.solver.orchestrator.infrastructure.messaging;

import dev.vgandolfi.solver.orchestrator.application.dto.messaging.JobResultMessage;
import dev.vgandolfi.solver.orchestrator.application.service.JobApplicationService;
import dev.vgandolfi.solver.orchestrator.domain.exception.JobNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome os resultados publicados pelo worker Python na fila de resultado e
 * delega ao caso de uso de aplicação.
 *
 * Resultados para jobs inexistentes (ex.: órfãos de falha anterior) são
 * tratados como descartáveis: o {@link JobNotFoundException} é capturado aqui
 * para evitar requeue infinito na fila (que não tem DLQ).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobResultConsumer {

    private final JobApplicationService jobApplicationService;

    @RabbitListener(queues = "${app.rabbitmq.result-queue}")
    public void onResult(JobResultMessage message) {
        log.info("result_received job={} solverStatus={} solverType={}",
                message.routingJobId(), message.solverStatus(), message.solverType());
        try {
            jobApplicationService.handleJobResult(message);
        } catch (JobNotFoundException ex) {
            log.warn("result_for_unknown_job_discarded job={} reason={}",
                    message.routingJobId(), ex.getMessage());
        }
    }
}