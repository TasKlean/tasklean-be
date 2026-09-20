package com.tasklean.api.security.ratelimit;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ErrorMessages;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Token-bucket rate limiting (Bucket4j). Runs after {@link com.tasklean.api.auth.jwt.JwtAuthenticationFilter}
 * so it can key by user id when authenticated. An authenticated request must pass <em>both</em> a
 * per-user and a per-IP bucket; an unauthenticated request is limited per IP by the endpoint's tier.
 * On rejection it writes a 429 with a {@code Retry-After} header - filters run before Spring MVC,
 * so it renders the JSON envelope itself rather than relying on {@code GlobalExceptionHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final BucketRegistry bucketRegistry;

    /**
     * Consumes a token from each applicable bucket; on the first exhausted bucket, responds 429.
     *
     * @param request     the incoming request
     * @param response    the response
     * @param filterChain the remaining chain
     * @throws ServletException if the downstream chain fails
     * @throws IOException      if writing the 429 body fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Long userId = currentUserId();

        for (LimitCheck check : checksFor(request.getRequestURI(), ip, userId)) {
            Bucket bucket = bucketRegistry.resolve(check.key(), check.tier());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                reject(response, probe.getNanosToWaitForRefill(), check.name(), ip, userId);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Skips rate limiting entirely when disabled, and for infrastructure paths (health, error).
     *
     * @param request the incoming request
     * @return {@code true} to bypass this filter
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.equals("/error");
    }

    private List<LimitCheck> checksFor(String path, String ip, Long userId) {
        if (userId != null) {
            // Authenticated: must pass both the per-user quota and a per-IP backstop.
            return List.of(
                    new LimitCheck("apiUser", "apiUser:" + userId, properties.getApiPerUser()),
                    new LimitCheck("apiIp", "apiIp:" + ip, properties.getApiPerIp()));
        }
        if (path.equals("/api/auth/register")) {
            return List.of(new LimitCheck("register", "register:" + ip, properties.getRegister()));
        }
        if (path.equals("/api/auth/refresh")) {
            return List.of(new LimitCheck("refresh", "refresh:" + ip, properties.getRefresh()));
        }
        if (path.startsWith("/api/auth/")) {
            return List.of(new LimitCheck("auth", "auth:" + ip, properties.getAuth()));
        }
        return List.of(new LimitCheck("apiIp", "apiIp:" + ip, properties.getApiPerIp()));
    }

    private void reject(HttpServletResponse response, long nanosToWait, String tier, String ip, Long userId)
            throws IOException {
        long retryAfterSeconds = Math.max(1, nanosToWait / 1_000_000_000L);
        // Security-relevant (possible abuse) → WARN for monitoring.
        log.warn("Rate limit exceeded: tier={} ip={} userId={}", tier, ip, userId);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Raw JSON to match the ApiResponse envelope — avoids an ObjectMapper dependency in the filter layer.
        response.getWriter().write("{\"success\":false,\"message\":\"" + ErrorMessages.RATE_LIMITED + "\",\"data\":null}");
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal.userId() : null;
    }

    private record LimitCheck(String name, String key, RateLimitProperties.Tier tier) {
    }
}
