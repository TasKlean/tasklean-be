package com.tasklean.api.auth.jwt;

import com.tasklean.api.common.logging.LogFields;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepts every request to identify the caller from their JWT token.
 * Does not reject requests — only sets the authenticated user in SecurityContext
 * so Spring's authorization layer can decide whether to allow or deny.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Extracts the Bearer token, validates it, and places the corresponding User
     * into SecurityContextHolder. If the token is missing or invalid, the request
     * continues anonymously (no rejection here).
     *
     * @param request     the incoming request
     * @param response    the response, passed along the chain
     * @param filterChain the remaining filter chain to continue
     * @throws ServletException if the downstream chain fails
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtService.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtService.extractEmail(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null && user.getIsActive()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Attach the caller to the log context so every line for this request is attributable.
                // Cleared centrally by RequestLoggingFilter's MDC.clear() once the request completes.
                MDC.put(LogFields.USER_ID, user.getUid());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Skips public auth endpoints — no point parsing tokens on login/register.
     *
     * @param request the incoming request
     * @return {@code true} for {@code /api/auth/} paths, which bypass this filter
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/");
    }
}
