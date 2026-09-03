package dev.vgandolfi.opt.orchestrator.infrastructure.controller;

import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CreateMatrixJobRequest;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CreateTspJobRequest;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.CreateVrpJobRequest;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.MatrixJobInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.TspJobInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.request.VrpJobInput;
import dev.vgandolfi.opt.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.opt.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.opt.orchestrator.application.mapper.JobInputMapper;
import dev.vgandolfi.opt.orchestrator.application.service.JobApplicationService;
import dev.vgandolfi.opt.orchestrator.application.validation.VrpFeasibilityValidator;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.opt.orchestrator.domain.enums.JobType;
import dev.vgandolfi.opt.orchestrator.domain.exception.JobNotFoundException;
import dev.vgandolfi.opt.orchestrator.domain.exception.VrpInfeasibleException;
import dev.vgandolfi.opt.orchestrator.infrastructure.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@ActiveProfiles("test")
class JobControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JobApplicationService jobApplicationService;
    @MockitoBean private JobInputMapper jobInputMapper;
    @MockitoBean private VrpFeasibilityValidator vrpFeasibilityValidator;

    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TSP_INPUT_JSON = "{\"matrix_type\":\"EUCLIDIAN\",\"origin\":{\"lat\":-23.5,\"lng\":-46.6}}";

    @Test
    void createTspJobReturns202Accepted() throws Exception {
        JobResponse response = pendingResponse();
        when(jobInputMapper.toTspInputJson(any(TspJobInput.class))).thenReturn(TSP_INPUT_JSON);
        when(jobApplicationService.createJob(eq(JobType.TSP), eq(TSP_INPUT_JSON), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/jobs/tsp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"webhookUrl":"https://hooks.example.com/cb","input":{
                                  "origin":{"lat":-23.5,"lng":-46.6},
                                  "stops":[{"id":"A","name":"Cliente A","location":{"lat":-23.55,"lng":-46.65}}]
                                }}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusUrl").value("http://localhost:8080/api/v1/jobs/" + JOB_ID));

        ArgumentCaptor<String> webhookCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobApplicationService).createJob(eq(JobType.TSP), eq(TSP_INPUT_JSON), webhookCaptor.capture(),
                any(), any());
        assertThat(webhookCaptor.getValue()).isEqualTo("https://hooks.example.com/cb");
    }

    @Test
    void createVrpJobReturns202Accepted() throws Exception {
        JobResponse response = pendingResponse();
        when(jobInputMapper.toVrpInputJson(any(VrpJobInput.class))).thenReturn(TSP_INPUT_JSON);
        when(jobApplicationService.createJob(eq(JobType.VRP), eq(TSP_INPUT_JSON), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/jobs/vrp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":{
                                  "origin":{"lat":-23.5,"lng":-46.6},
                                  "clients":[{"id":"c1","name":"Cliente 1","location":{"lat":-23.55,"lng":-46.65}}],
                                  "vehicles":[{"name":"Van 1"}]
                                }}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createVrpJobWithInfeasibleFleetReturns422WithoutPublishing() throws Exception {
        doThrow(new VrpInfeasibleException("volume",
                "Fleet capacity insufficient: total volume 130L > 120L available"))
                .when(vrpFeasibilityValidator).validate(any(VrpJobInput.class));

        mockMvc.perform(post("/api/v1/jobs/vrp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":{
                                  "origin":{"lat":-23.5,"lng":-46.6},
                                  "clients":[{"id":"c1","name":"Cliente 1","location":{"lat":-23.55,"lng":-46.65}}],
                                  "vehicles":[{"name":"Van 1"}]
                                }}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Infeasible VRP"))
                .andExpect(jsonPath("$.fields.volume")
                        .value("Fleet capacity insufficient: total volume 130L > 120L available"));

        // Nada é publicado (nem mapeado, nem criado/enviado à fila).
        verify(jobInputMapper, never()).toVrpInputJson(any());
        verify(jobApplicationService, never()).createJob(any(), any(), any(), any(), any());
    }

    @Test
    void createMatrixJobReturns202Accepted() throws Exception {
        JobResponse response = pendingResponse();
        when(jobInputMapper.toMatrixInputJson(any(MatrixJobInput.class))).thenReturn(TSP_INPUT_JSON);
        when(jobApplicationService.createJob(eq(JobType.DISTANCE_MATRIX), eq(TSP_INPUT_JSON), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/jobs/distance-matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":{"coordinates":[{"lat":-23.5,"lng":-46.6},{"lat":-23.55,"lng":-46.65}]}}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTspJobWithEmptyStopsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/tsp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":{"origin":{"lat":-23.5,"lng":-46.6},"stops":[]}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void createTspJobWithOutOfRangeCoordinateReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/tsp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":{
                                  "origin":{"lat":91,"lng":0},
                                  "stops":[{"id":"A","location":{"lat":0,"lng":0}}]
                                }}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void createVrpJobWithoutInputReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/vrp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookUrl\":\"https://hooks.example.com/cb\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void getJobStatusReturns200() throws Exception {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        JobStatusResponse response = new JobStatusResponse(
                JOB_ID, JobType.TSP, JobStatus.DONE,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID + "/input",
                "http://localhost:8080/api/v1/jobs/" + JOB_ID + "/output",
                "http://localhost:8080/api/v1/jobs/" + JOB_ID,
                "https://hooks.example.com/cb",
                null, 1000L,
                createdAt,
                createdAt.plusSeconds(5),
                createdAt.plusSeconds(30),
                "inputs/" + JOB_ID + ".json",
                "solutions/" + JOB_ID + ".json");
        when(jobApplicationService.getJobStatus(JOB_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.type").value("TSP"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.inputUrl").value("http://localhost:8080/api/v1/jobs/" + JOB_ID + "/input"))
                .andExpect(jsonPath("$.outputUrl").value("http://localhost:8080/api/v1/jobs/" + JOB_ID + "/output"))
                .andExpect(jsonPath("$.statusUrl").value("http://localhost:8080/api/v1/jobs/" + JOB_ID))
                .andExpect(jsonPath("$.webhookUrl").value("https://hooks.example.com/cb"))
                .andExpect(jsonPath("$.processingTimeMs").value(1000))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.startedAt").value("2026-01-01T10:00:05Z"))
                .andExpect(jsonPath("$.finishedAt").value("2026-01-01T10:00:30Z"))
                .andExpect(jsonPath("$.inputPath").value("inputs/" + JOB_ID + ".json"))
                .andExpect(jsonPath("$.outputPath").value("solutions/" + JOB_ID + ".json"));
    }

    @Test
    void getJobStatusReturnsPendingWithNullOutputAndTimestamps() throws Exception {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        JobStatusResponse response = new JobStatusResponse(
                JOB_ID, JobType.VRP, JobStatus.PENDING,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID + "/input",
                null,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID,
                null, null, null,
                createdAt, null, null,
                "inputs/" + JOB_ID + ".json", null);
        when(jobApplicationService.getJobStatus(JOB_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.outputUrl").doesNotExist())
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.outputPath").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.inputPath").value("inputs/" + JOB_ID + ".json"));
    }

    @Test
    void getJobStatusReturns404WhenNotFound() throws Exception {
        when(jobApplicationService.getJobStatus(JOB_ID))
                .thenThrow(new JobNotFoundException(JOB_ID));

        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Job not found: " + JOB_ID));
    }

    @Test
    void getOutputReturnsJsonWhenDone() throws Exception {
        when(jobApplicationService.getOutputJson(JOB_ID)).thenReturn("{\"routes\":[]}");

        mockMvc.perform(get("/api/v1/jobs/{id}/output", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"routes\":[]}"));
    }

    @Test
    void getOutputReturns409WhenNotReady() throws Exception {
        when(jobApplicationService.getOutputJson(JOB_ID))
                .thenThrow(new IllegalStateException("Output not ready for job " + JOB_ID));

        mockMvc.perform(get("/api/v1/jobs/{id}/output", JOB_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void getOutputReturns404WhenJobMissing() throws Exception {
        when(jobApplicationService.getOutputJson(JOB_ID))
                .thenThrow(new JobNotFoundException(JOB_ID));

        mockMvc.perform(get("/api/v1/jobs/{id}/output", JOB_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInputReturnsJson() throws Exception {
        when(jobApplicationService.getInputJson(JOB_ID)).thenReturn("{\"matrix_type\":\"EUCLIDIAN\"}");

        mockMvc.perform(get("/api/v1/jobs/{id}/input", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"matrix_type\":\"EUCLIDIAN\"}"));
    }

    @Test
    void getInputReturns404WhenJobMissing() throws Exception {
        when(jobApplicationService.getInputJson(JOB_ID))
                .thenThrow(new JobNotFoundException(JOB_ID));

        mockMvc.perform(get("/api/v1/jobs/{id}/input", JOB_ID))
                .andExpect(status().isNotFound());
    }

    private JobResponse pendingResponse() {
        return new JobResponse(JOB_ID, JobType.TSP, JobStatus.PENDING,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID + "/input", null,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID, Instant.now(), null, null, null);
    }
}
