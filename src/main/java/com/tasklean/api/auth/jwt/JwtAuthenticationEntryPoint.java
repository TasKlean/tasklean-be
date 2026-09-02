package com.tasklean.api.auth.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles rejected unauthenticated requests. Spring Security calls this when
 * a request fails authorization (no valid token) and needs a 401 response.
 * Without this, Spring would return a default HTML error page instead of JSON.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Writes a 401 JSON response matching our ApiResponse envelope format.
     *
     * @param request       the request that failed authentication
     * @param response      the response to write the 401 body into
     * @param authException the authentication failure
     * @throws IOException if writing the response body fails
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Raw JSON to match ApiResponse envelope — avoids ObjectMapper dependency in the filter layer
        response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}");
    }
}
