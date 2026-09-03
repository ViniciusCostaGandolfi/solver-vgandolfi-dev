package dev.vgandolfi.opt.orchestrator.infrastructure.controller;

import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CreateMatrixJobRequest;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CreateTspJobRequest;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CreateVrpJobRequest;
import dev.vgandolfi.opt.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.opt.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.opt.orchestrator.application.mapper.JobInputMapper;
import dev.vgandolfi.opt.orchestrator.application.service.JobApplicationService;
import dev.vgandolfi.opt.orchestrator.application.validation.VrpFeasibilityValidator;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Validated
public class JobController {

    private final JobApplicationService jobApplicationService;
    private final JobInputMapper jobInputMapper;
    private final VrpFeasibilityValidator vrpFeasibilityValidator;

    @PostMapping("/tsp")
    public ResponseEntity<JobResponse> createTspJob(@Valid @RequestBody CreateTspJobRequest request,
                                                    HttpServletRequest httpRequest) {
        String inputJson = jobInputMapper.toTspInputJson(request.input());
        return createJob(JobType.TSP, inputJson, request.webhookUrl(), httpRequest);
    }

    @PostMapping("/vrp")
    public ResponseEntity<JobResponse> createVrpJob(@Valid @RequestBody CreateVrpJobRequest request,
                                                    HttpServletRequest httpRequest) {
        // Viabilidade checada no Java ANTES de gerar o payload e publicar na fila:
        // VRP inviável nunca chega ao worker (nem ao S3).
        vrpFeasibilityValidator.validate(request.input());
        String inputJson = jobInputMapper.toVrpInputJson(request.input());
        return createJob(JobType.VRP, inputJson, request.webhookUrl(), httpRequest);
    }

    @PostMapping("/distance-matrix")
    public ResponseEntity<JobResponse> createMatrixJob(@Valid @RequestBody CreateMatrixJobRequest request,
                                                       HttpServletRequest httpRequest) {
        String inputJson = jobInputMapper.toMatrixInputJson(request.input());
        return createJob(JobType.DISTANCE_MATRIX, inputJson, request.webhookUrl(), httpRequest);
    }

    private ResponseEntity<JobResponse> createJob(JobType type, String inputJson, String webhookUrl,
                                                  HttpServletRequest httpRequest) {
        JobResponse response = jobApplicationService.createJob(type, inputJson, webhookUrl,
                httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(jobApplicationService.getJobStatus(id));
    }

    @GetMapping("/{id}/output")
    public ResponseEntity<String> getOutput(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jobApplicationService.getOutputJson(id));
    }

    @GetMapping("/{id}/input")
    public ResponseEntity<String> getInput(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jobApplicationService.getInputJson(id));
    }
}
