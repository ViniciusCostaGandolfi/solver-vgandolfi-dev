package dev.vgandolfi.opt.orchestrator.infrastructure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RateLimitProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário do {@link RateLimitFilter} com {@link StringRedisTemplate}
 * mockado: limite exato por IP/chave Redis, isolamento entre IPs, formas de IP
 * distintas (::1 vs 127.0.0.1) com chaves Redis diferentes, o fluxo
 * INCR+EXPIRE via Lua, e fail-open quando o Redis está indisponível.
 */
class RateLimitFilterUnitTest {

    private static final String POLL_URI = "/api/v1/jobs/11111111-1111-1111-1111-111111111111";

    private RateLimitProperties properties;
    private StringRedisTemplate redis;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        properties = mock(RateLimitProperties.class);
        redis = mock(StringRedisTemplate.class);
        filter = new RateLimitFilter(properties, redis);
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        return request;
    }

    /** Faz o mock do INCR+EXPIRE devolver um contador crescente por chave Redis. */
    private void stubIncrementingCounters() {
        ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<String> keys = invocation.getArgument(1);
                    return counters.computeIfAbsent(keys.get(0), k -> new AtomicLong()).incrementAndGet();
                });
    }

    @Test
    void allowsExactlyPollsPerMinuteRequestsPerIpThenRejects() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(5);
        stubIncrementingCounters();
        when(redis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(60000L);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString())
                .contains("\"error\":\"Rate limit exceeded\"")
                .contains("\"retryAfterSeconds\":60");

        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void bucketsAreIsolatedPerIp() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(2);
        stubIncrementingCounters();

        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        MockHttpServletResponse otherIp = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.2"), otherIp, chain);
        assertThat(otherIp.getStatus()).isEqualTo(200);

        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void differentIpFormsProduceDistinctRedisKeys() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(5);
        List<String> usedKeys = new ArrayList<>();
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<String> keys = invocation.getArgument(1);
                    usedKeys.add(keys.get(0));
                    return 1L;
                });

        filter.doFilter(request("GET", POLL_URI, "::1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("GET", POLL_URI, "127.0.0.1"), new MockHttpServletResponse(), chain);

        assertThat(usedKeys).hasSize(2);
        assertThat(usedKeys.get(0)).isEqualTo("ratelimit:polls:::1");
        assertThat(usedKeys.get(1)).isEqualTo("ratelimit:polls:127.0.0.1");
        assertThat(usedKeys.get(0)).isNotEqualTo(usedKeys.get(1));
    }

    @Test
    void sameIpUsesSameRedisKeyAcrossRequests() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(5);
        stubIncrementingCounters();

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redis, times(3)).execute(any(RedisScript.class), keysCaptor.capture(), any());
        assertThat(keysCaptor.getAllValues()).allSatisfy(keys ->
                assertThat(keys).containsExactly("ratelimit:polls:10.0.0.1"));
    }

    @Test
    void respectsHighConfiguredLimit() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(600);
        stubIncrementingCounters();

        for (int i = 0; i < 600; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        verify(chain, times(600)).doFilter(any(), any());
    }

    @Test
    void jobCreationAndGeoBucketsAreRateLimited() throws Exception {
        when(properties.jobsPerMinute()).thenReturn(2);
        when(properties.geoPerMinute()).thenReturn(2);
        stubIncrementingCounters();

        filter.doFilter(request("POST", "/api/v1/jobs/tsp", "10.0.0.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("POST", "/api/v1/jobs/tsp", "10.0.0.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blockedJob = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/jobs/tsp", "10.0.0.1"), blockedJob, chain);
        assertThat(blockedJob.getStatus()).isEqualTo(429);

        filter.doFilter(request("GET", "/api/v1/geo/geocode?address=x", "10.0.0.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("GET", "/api/v1/geo/geocode?address=x", "10.0.0.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blockedGeo = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/v1/geo/geocode?address=x", "10.0.0.1"), blockedGeo, chain);
        assertThat(blockedGeo.getStatus()).isEqualTo(429);

        // A rota de CEP também é coberta pelo bucket geo (GET /api/v1/geo/**).
        filter.doFilter(request("GET", "/api/v1/geo/cep/01001000", "10.0.0.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("GET", "/api/v1/geo/cep/01001000", "10.0.0.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blockedCep = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/v1/geo/cep/01001000", "10.0.0.1"), blockedCep, chain);
        assertThat(blockedCep.getStatus()).isEqualTo(429);
    }

    @Test
    void nonPollEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", "/api/v1/jobs/11111111-1111-1111-1111-111111111111/input", "10.0.0.1"),
                    response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(10)).doFilter(any(), any());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void redisFailureFailsOpenAndAllowsRequest() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(5);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void retryAfterFallsBackToSixtyWhenTtlUnavailable() throws Exception {
        when(properties.pollsPerMinute()).thenReturn(1);
        stubIncrementingCounters();
        when(redis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(null);

        // 1º request dentro do limite passa; 2º excede e deve retornar 429.
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), ok, chain);
        assertThat(ok.getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("GET", POLL_URI, "10.0.0.1"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("\"retryAfterSeconds\":60");
    }

    @Test
    void scriptImplementsAtomicIncrWithConditionalExpire() throws Exception {
        String script = RateLimitFilter.INCR_EXPIRE_SCRIPT.getScriptAsString();
        assertThat(script).contains("INCR", "KEYS[1]");
        assertThat(script).contains("EXPIRE", "KEYS[1]", "ARGV[1]");
        assertThat(script).contains("current == 1");
    }
}