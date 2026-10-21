package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.twofactor.dto.TwoFactorStatusResponse;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link TwoFactorController}: every endpoint requires an
 * authenticated caller and acts on the id taken from the JWT principal (no cross-user access).
 */
@WebMvcTest(TwoFactorController.class)
@Import(MethodSecuritySliceConfig.class)
class TwoFactorControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TwoFactorService twoFactorService;

    // Pulled into the slice by the @Component JWT filter; mocked so the context builds.
    @MockitoBean
    private JwtService jwtService;

    private RequestPostProcessor as(Long userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "usr-" + userId, "user@example.com", UserRole.USER);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // A body is sent on every POST so the request reaches the authorization check instead of
    // failing argument resolution first. The slice has no authentication entry point, so a denial
    // surfaces as 403 here while the real filter chain answers 401.
    private RequestBuilder anonymousPost(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void getStatus_unauthenticated_denied() throws Exception {
        mockMvc.perform(get("/api/account/2fa"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getStatus_authenticated_usesCallerFromToken() throws Exception {
        when(twoFactorService.getStatus(7L)).thenReturn(new TwoFactorStatusResponse(false, false, null, 0L));

        mockMvc.perform(get("/api/account/2fa").with(as(7L)))
                .andExpect(status().isOk());
    }

    @Test
    void startSetup_unauthenticated_denied() throws Exception {
        mockMvc.perform(anonymousPost("/api/account/2fa/setup", "{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void enable_unauthenticated_denied() throws Exception {
        mockMvc.perform(anonymousPost("/api/account/2fa/enable", "{\"code\":\"123456\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void disable_unauthenticated_denied() throws Exception {
        mockMvc.perform(anonymousPost("/api/account/2fa/disable", "{\"password\":\"pw\",\"code\":\"123456\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void regenerateRecoveryCodes_unauthenticated_denied() throws Exception {
        mockMvc.perform(anonymousPost("/api/account/2fa/recovery-codes", "{\"password\":\"pw\",\"code\":\"123456\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void enable_authenticatedWithoutCode_rejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/account/2fa/enable").with(as(7L))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
