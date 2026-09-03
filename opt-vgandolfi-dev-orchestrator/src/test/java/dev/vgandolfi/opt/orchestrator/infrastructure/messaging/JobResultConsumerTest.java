package dev.vgandolfi.opt.orchestrator.infrastructure.messaging;

import dev.vgandolfi.opt.orchestrator.application.dto.messaging.JobResultMessage;
import dev.vgandolfi.opt.orchestrator.application.service.JobApplicationService;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.domain.exception.JobNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobResultConsumerTest {

    @Mock private JobApplicationService jobApplicationService;

    private JobResultMessage message() {
        return new JobResultMessage(UUID.randomUUID(), JobType.TSP,
                "inputs/x.json", "solutions/y.json", 100L, "OPTIMAL", null, null,
                "LKH_TSP", "TspResponse", UUID.randomUUID(), 1234.5, 3, 1);
    }

    @Test
    void delegatesResultMessageToService() {
        JobResultConsumer consumer = new JobResultConsumer(jobApplicationService);
        JobResultMessage message = message();

        consumer.onResult(message);

        verify(jobApplicationService).handleJobResult(message);
    }

    @Test
    void discardsMessageWhenJobNotFoundInsteadOfRethrowing() {
        JobResultConsumer consumer = new JobResultConsumer(jobApplicationService);
        JobResultMessage message = message();
        doThrow(new JobNotFoundException(message.routingJobId()))
                .when(jobApplicationService).handleJobResult(message);

        assertThatCode(() -> consumer.onResult(message))
                .doesNotThrowAnyException();

        verify(jobApplicationService).handleJobResult(message);
    }
}