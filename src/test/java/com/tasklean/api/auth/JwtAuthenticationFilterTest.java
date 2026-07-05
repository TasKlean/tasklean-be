package com.tasklean.api.auth;

import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

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
    void validToken_setsAuthenticationInSecurityContext() throws ServletException, IOException {
        User user = User.builder()
                .idUser(1L)
                .email("alice@example.com")
                .isActive(true)
                .build();

        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
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
    void validToken_inactiveUser_doesNotAuthenticate() throws ServletException, IOException {
        User inactiveUser = User.builder()
                .idUser(1L)
                .email("alice@example.com")
                .isActive(false)
                .build();

        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(inactiveUser));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validToken_userNotInDb_doesNotAuthenticate() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("deleted@example.com");
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.empty());

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
