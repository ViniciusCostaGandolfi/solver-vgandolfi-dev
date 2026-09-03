package dev.vgandolfi.solver.orchestrator.infrastructure.security;

import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobResponse;
import dev.vgandolfi.solver.orchestrator.application.dto.job.response.JobStatusResponse;
import dev.vgandolfi.solver.orchestrator.application.service.GeocodingService;
import dev.vgandolfi.solver.orchestrator.application.service.JobApplicationService;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobStatus;
import dev.vgandolfi.solver.orchestrator.domain.enums.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o {@link RateLimitFilter} em contexto completo com o
 * {@link StringRedisTemplate} mockado (sem Redis real): o INCR+EXPIRE é
 * simulado por contador crescente por chave Redis. O profile "test" define
 * jobs-per-minute=3, polls-per-minute=5 e geo-per-minute=3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFilterTest {

    private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String VALID_BODY = """
            {"input":{"origin":{"lat":-23.5,"lng":-46.6},
                       "stops":[{"id":"A","location":{"lat":-23.55,"lng":-46.65}}]}}""";

    private static JobStatusResponse pendingStatus() {
        return new JobStatusResponse(JOB_ID, JobType.TSP, JobStatus.PENDING,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID + "/input",
                null,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID,
                null, null, null,
                Instant.now(), null, null,
                "inputs/" + JOB_ID + ".json", null);
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JobApplicationService jobApplicationService;
    @MockitoBean private GeocodingService geocodingService;
    @MockitoBean private StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @BeforeEach
    void stubRedis() {
        counters.clear();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<String> keys = invocation.getArgument(1);
                    return counters.computeIfAbsent(keys.get(0), k -> new AtomicLong()).incrementAndGet();
                });
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(60000L);
    }

    @Test
    void returns429AfterJobCreationLimit() throws Exception {
        JobResponse response = new JobResponse(JOB_ID, JobType.TSP, JobStatus.PENDING,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID + "/input", null,
                "http://localhost:8080/api/v1/jobs/" + JOB_ID, Instant.now(), null, null, null);
        when(jobApplicationService.createJob(any(), anyString(), any(), any(), any())).thenReturn(response);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/jobs/tsp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/v1/jobs/tsp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test
    void returns429AfterPollLimit() throws Exception {
        JobStatusResponse response = pendingStatus();
        when(jobApplicationService.getJobStatus(JOB_ID)).thenReturn(response);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"));
    }

    @Test
    void pollLimitIsIsolatedPerIp() throws Exception {
        JobStatusResponse response = pendingStatus();
        when(jobApplicationService.getJobStatus(JOB_ID)).thenReturn(response);

        // IP A esgota o próprio bucket após 5 requests (polls-per-minute=5 no profile test).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID)
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.50");
                                return req;
                            }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID)
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.50");
                            return req;
                        }))
                .andExpect(status().isTooManyRequests());

        // IP B tem bucket próprio e continua permitido mesmo com A esgotado.
        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID)
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.51");
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    @Test
    void returns429AfterGeoLimit() throws Exception {
        when(geocodingService.geocode("rua augusta")).thenReturn(List.of());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/geo/geocode").param("address", "rua augusta"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/geo/geocode").param("address", "rua augusta"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test
    void healthEndpointsAreNotRateLimited() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }
}