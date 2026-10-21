package com.tasklean.api.domain.usersettings;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.domain.usersettings.dto.UserSettingsResponse;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link UserSettingsController}: both endpoints require an
 * authenticated caller and act on the id taken from the JWT principal (no cross-user access).
 */
@WebMvcTest(UserSettingsController.class)
@Import(MethodSecuritySliceConfig.class)
class UserSettingsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSettingsService userSettingsService;

    // Pulled into the slice by the @Component JWT filter; mocked so the context builds.
    @MockitoBean
    private JwtService jwtService;

    private RequestPostProcessor as(Long userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "usr-" + userId, "user@example.com", UserRole.USER);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void getMySettings_unauthenticated_denied() throws Exception {
        mockMvc.perform(get("/api/user-settings"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMySettings_authenticated_resolvesCallerFromToken() throws Exception {
        when(userSettingsService.getMySettings(1L))
                .thenReturn(new UserSettingsResponse(Theme.DARK, AppLocale.SL));

        mockMvc.perform(get("/api/user-settings").with(as(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("DARK"))
                .andExpect(jsonPath("$.data.locale").value("SL"));
    }

    @Test
    void updateMySettings_unauthenticated_denied() throws Exception {
        mockMvc.perform(put("/api/user-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"locale\":\"SL\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updateMySettings_authenticated_ok() throws Exception {
        when(userSettingsService.updateMySettings(eq(1L), any()))
                .thenReturn(new UserSettingsResponse(Theme.LIGHT, AppLocale.DE));

        mockMvc.perform(put("/api/user-settings")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"LIGHT\",\"locale\":\"DE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("LIGHT"));
    }

    // --- cross-user isolation: the acted-on id comes only from the token ---

    @Test
    void updateMySettings_actsOnTheCallerFromTheToken_notAnotherUser() throws Exception {
        when(userSettingsService.updateMySettings(eq(7L), any()))
                .thenReturn(new UserSettingsResponse(Theme.DARK, AppLocale.EN));

        mockMvc.perform(put("/api/user-settings")
                        .with(as(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"locale\":\"EN\"}"))
                .andExpect(status().isOk());

        // Only the token's user is ever touched.
        verify(userSettingsService).updateMySettings(eq(7L), any());
        verify(userSettingsService, never()).updateMySettings(eq(1L), any());
    }

    @Test
    void updateMySettings_bodyCarryingForeignIds_ignoresThemAndUsesTheToken() throws Exception {
        when(userSettingsService.updateMySettings(eq(1L), any()))
                .thenReturn(new UserSettingsResponse(Theme.DARK, AppLocale.EN));

        // A client trying to retarget another user via the body: the request DTO has no id
        // fields at all, so these are silently dropped and the caller's own id is used.
        mockMvc.perform(put("/api/user-settings")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"locale\":\"EN\","
                                + "\"userId\":999,\"idUserSettings\":42,\"user\":{\"idUser\":999}}"))
                .andExpect(status().isOk());

        verify(userSettingsService).updateMySettings(eq(1L), any());
        verify(userSettingsService, never()).updateMySettings(eq(999L), any());
    }
}
