package com.tasklean.api.security.ratelimit;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitProperties props;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        filter = new RateLimitFilter(props, new CaffeineBucketRegistry());
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(uri);
        r.setRemoteAddr("1.2.3.4");
        return r;
    }

    private void authenticate(long userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "usr", "u@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    private MockHttpServletResponse run(String uri) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request(uri), response, chain);
        return response;
    }

    private RateLimitProperties.Tier tier(int capacity) {
        return new RateLimitProperties.Tier(capacity, Duration.ofMinutes(1));
    }

    // --- unauthenticated (per-IP) ---

    @Test
    void authEndpoint_withinLimit_passesThrough() throws Exception {
        props.setAuth(tier(2));

        MockHttpServletResponse response = run("/api/auth/login");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void authEndpoint_overLimit_returns429() throws Exception {
        props.setAuth(tier(1));

        run("/api/auth/login");                             // consumes the only token
        MockHttpServletResponse response = run("/api/auth/login");   // exhausted

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Too many requests");
        verify(chain, times(1)).doFilter(any(), any());     // only the first request passed
    }

    // --- authenticated (per-user AND per-IP) ---

    @Test
    void authenticated_withinBothLimits_passes() throws Exception {
        authenticate(1L);
        props.setApiPerUser(tier(5));
        props.setApiPerIp(tier(5));

        MockHttpServletResponse response = run("/api/tasks");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void authenticated_overUserLimit_returns429() throws Exception {
        authenticate(1L);
        props.setApiPerUser(tier(1));
        props.setApiPerIp(tier(100));

        run("/api/tasks");
        MockHttpServletResponse response = run("/api/tasks");

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void authenticated_overIpLimit_returns429() throws Exception {
        authenticate(1L);
        props.setApiPerUser(tier(100));
        props.setApiPerIp(tier(1));

        run("/api/tasks");
        MockHttpServletResponse response = run("/api/tasks");

        assertThat(response.getStatus()).isEqualTo(429);
    }

    // --- shouldNotFilter ---

    @Test
    void shouldNotFilter_whenDisabled_returnsTrue() {
        props.setEnabled(false);

        assertThat(filter.shouldNotFilter(request("/api/tasks"))).isTrue();
    }

    @Test
    void shouldNotFilter_actuatorAndError_returnsTrue() {
        assertThat(filter.shouldNotFilter(request("/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/error"))).isTrue();
    }

    @Test
    void shouldNotFilter_normalPath_returnsFalse() {
        assertThat(filter.shouldNotFilter(request("/api/tasks"))).isFalse();
    }
}
