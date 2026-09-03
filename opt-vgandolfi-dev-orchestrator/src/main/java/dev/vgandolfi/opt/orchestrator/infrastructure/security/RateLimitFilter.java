package dev.vgandolfi.opt.orchestrator.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.RateLimitProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate limiting por IP via Redis (fixed window de 60s), filtro puro sem Spring
 * Security. Ao contrário dos buckets Bucket4j em memória (separados por forma
 * de IP e presos a uma instância), o Redis dá chaves com TTL compartilhadas
 * entre instâncias e que se auto-expiram após a janela.
 * <ul>
 *   <li>POST /api/v1/jobs/{tsp|vrp|distance-matrix} → {@code ratelimit:jobs:{ip}}</li>
 *   <li>GET /api/v1/jobs/{id} e /api/v1/jobs/{id}/output → {@code ratelimit:polls:{ip}}</li>
 *   <li>GET /api/v1/geo/** → {@code ratelimit:geo:{ip}}</li>
 * </ul>
 * Usa {@code request.getRemoteAddr()} (IP da conexão direta) — nunca confia em
 * X-Forwarded-For sem proxy confiável.
 *
 * <p>Indisponibilidade do Redis é <strong>fail-open</strong>: a requisição é
 * liberada e um warn é logado. Decisão consistente com o estilo fail-open do
 * projeto (ex. {@code WebhookNotifier}): uma falha do Redis não deve derrubar
 * o tráfego da API.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_SECONDS = 60L;
    static final long RETRY_AFTER_SECONDS = 60L;

    private static final String KEY_PREFIX_JOBS = "ratelimit:jobs:";
    private static final String KEY_PREFIX_POLLS = "ratelimit:polls:";
    private static final String KEY_PREFIX_GEO = "ratelimit:geo:";

    /**
     * Fixed window atômico: INCR e, apenas quando é a 1ª requisição da janela,
     * define o EXPIRE da chave em 60s. O contador é devolvido para o filtro
     * comparar com o limite configurado.
     */
    static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final RateLimitProperties properties;
    private final StringRedisTemplate redis;

    /**
     * Loga os limites efetivos na inicialização — importante porque um
     * application-*.yml de profile pode sobrescrever as env vars
     * {@code RATE_LIMIT_*_PER_MINUTE} silenciosamente.
     */
    @PostConstruct
    void logEffectiveLimits() {
        log.info("rate_limit_effective jobsPerMinute={} pollsPerMinute={} geoPerMinute={}",
                properties.jobsPerMinute(), properties.pollsPerMinute(), properties.geoPerMinute());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String ip = request.getRemoteAddr();

        if (isJobCreation(method, uri)) {
            if (isAllowed(KEY_PREFIX_JOBS + ip, properties.jobsPerMinute(), response)) {
                filterChain.doFilter(request, response);
            }
        } else if (isPoll(method, uri)) {
            if (isAllowed(KEY_PREFIX_POLLS + ip, properties.pollsPerMinute(), response)) {
                filterChain.doFilter(request, response);
            }
        } else if (isGeo(method, uri)) {
            if (isAllowed(KEY_PREFIX_GEO + ip, properties.geoPerMinute(), response)) {
                filterChain.doFilter(request, response);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean isJobCreation(String method, String uri) {
        return HttpMethod.POST.matches(method)
                && uri.matches("^/api/v1/jobs/(tsp|vrp|distance-matrix)$");
    }

    private boolean isPoll(String method, String uri) {
        return HttpMethod.GET.matches(method)
                && (uri.matches("^/api/v1/jobs/[^/]+$") || uri.matches("^/api/v1/jobs/[^/]+/output$"));
    }

    private boolean isGeo(String method, String uri) {
        return HttpMethod.GET.matches(method) && uri.startsWith("/api/v1/geo/");
    }

    /**
     * Executa o INCR+EXPIRE atômico e libera a requisição se o contador não
     * excedeu o limite. Com Redis indisponível, libera (fail-open) e loga warn.
     */
    private boolean isAllowed(String key, long limit, HttpServletResponse response) throws IOException {
        try {
            Long current = redis.execute(INCR_EXPIRE_SCRIPT, List.of(key), String.valueOf(WINDOW_SECONDS));
            if (current != null && current > limit) {
                reject(response, key);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("redis_unavailable_fail_open key={} cause={}", key, ex.getMessage());
            return true;
        }
    }

    private void reject(HttpServletResponse response, String key) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":" + retryAfterSeconds(key) + "}");
    }

    /**
     * Tempo de espera derivado do TTL restante da chave (arredondado para cima,
     * limitado a 60s) para manter compatibilidade com o retryAfterSeconds anterior.
     */
    private long retryAfterSeconds(String key) {
        try {
            Long ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
            if (ttlMillis != null && ttlMillis > 0) {
                long seconds = (long) Math.ceil(ttlMillis / 1000.0);
                return Math.min(seconds, RETRY_AFTER_SECONDS);
            }
        } catch (Exception ex) {
            log.warn("redis_ttl_unavailable_fail_open key={} cause={}", key, ex.getMessage());
        }
        return RETRY_AFTER_SECONDS;
    }
}