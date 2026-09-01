package com.tasklean.api.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Cross-cutting request instrumentation: assigns each request a correlation id, exposes the
 * request context via MDC (so every log line emitted while handling it carries requestId/method/path/userId),
 * and emits a single summary line on completion.
 *
 * <p>This is why baseline observability is automatic — every current and future endpoint is covered
 * without per-controller effort. It runs before the Spring Security chain so even rejected (401) requests
 * are logged and correlated. MDC is always cleared in the finally block to avoid leaking context onto the
 * pooled worker thread's next request.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(LogFields.REQUEST_ID, requestId);
        MDC.put(LogFields.METHOD, request.getMethod());
        MDC.put(LogFields.PATH, request.getRequestURI());
        response.setHeader(LogFields.REQUEST_ID_HEADER, requestId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            MDC.put(LogFields.STATUS, Integer.toString(response.getStatus()));
            MDC.put(LogFields.DURATION_MS, Long.toString(durationMs));
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.clear();
        }
    }

    /** Adopt a caller-supplied correlation id if one arrives; otherwise mint a fresh one. */
    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(LogFields.REQUEST_ID_HEADER);
        return (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
    }

    /** Health checks are hit constantly by uptime monitors; logging them would drown the signal. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }
}
