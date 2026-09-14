package com.tasklean.api.auth.jwt;

import com.tasklean.api.domain.user.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void validToken_setsAuthPrincipalWithRoleAuthority() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractRole("valid-token")).thenReturn(UserRole.ADMIN);
        when(jwtService.extractUserId("valid-token")).thenReturn(1L);
        when(jwtService.extractUid("valid-token")).thenReturn("usr-abc-123");
        when(jwtService.extractEmail("valid-token")).thenReturn("alice@example.com");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthPrincipal.class);
        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(1L);
        assertThat(principal.uid()).isEqualTo("usr-abc-123");
        assertThat(principal.email()).isEqualTo("alice@example.com");
        assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noAuthHeader_continuesWithoutAuthentication() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void nonBearerHeader_continuesWithoutAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void invalidToken_continuesWithoutAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtService.validateToken("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_authEndpoints_returnsTrue() {
        request.setRequestURI("/api/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        request.setRequestURI("/api/auth/register");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_protectedEndpoints_returnsFalse() {
        request.setRequestURI("/api/tasks");
        assertThat(filter.shouldNotFilter(request)).isFalse();

        request.setRequestURI("/api/groups");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
