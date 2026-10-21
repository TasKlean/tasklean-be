package com.tasklean.api.domain.notificationsettings;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.notification.NotificationType;
import com.tasklean.api.domain.notificationsettings.dto.NotificationSettingResponse;
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
 * Verifies the @PreAuthorize wiring on {@link NotificationSettingsController}: both endpoints
 * require an authenticated caller and act on the id taken from the JWT principal, with the
 * notification type as the only path variable (no cross-user access).
 */
@WebMvcTest(NotificationSettingsController.class)
@Import(MethodSecuritySliceConfig.class)
class NotificationSettingsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSettingsService notificationSettingsService;

    // Pulled into the slice by the @Component JWT filter; mocked so the context builds.
    @MockitoBean
    private JwtService jwtService;

    private RequestPostProcessor as(Long userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "usr-" + userId, "user@example.com", UserRole.USER);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static final String BODY = "{\"inApp\":false,\"email\":true}";

    @Test
    void getMyNotificationSettings_unauthenticated_denied() throws Exception {
        // The slice has no entry point, so a denial surfaces as 403 here; the real chain
        // answers 401 for an anonymous caller.
        mockMvc.perform(get("/api/notification-settings"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMyNotificationSettings_authenticated_resolvesCallerFromToken() throws Exception {
        when(notificationSettingsService.getMySettings(1L)).thenReturn(List.of(
                new NotificationSettingResponse(NotificationType.TASK_ASSIGNED, true, false)));

        mockMvc.perform(get("/api/notification-settings").with(as(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.data[0].email").value(false));

        verify(notificationSettingsService).getMySettings(1L);
    }

    @Test
    void updateMyNotificationSetting_unauthenticated_denied() throws Exception {
        mockMvc.perform(put("/api/notification-settings/TASK_ASSIGNED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().is4xxClientError());

        verify(notificationSettingsService, never()).updateMySetting(any(), any(), any());
    }

    @Test
    void updateMyNotificationSetting_authenticated_ok() throws Exception {
        when(notificationSettingsService.updateMySetting(eq(1L), eq(NotificationType.TASK_REMINDER), any()))
                .thenReturn(new NotificationSettingResponse(NotificationType.TASK_REMINDER, false, true));

        mockMvc.perform(put("/api/notification-settings/TASK_REMINDER")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inApp").value(false));
    }

    @Test
    void updateMyNotificationSetting_unknownType_badRequest() throws Exception {
        mockMvc.perform(put("/api/notification-settings/NOT_A_TYPE")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());

        verify(notificationSettingsService, never()).updateMySetting(any(), any(), any());
    }

    @Test
    void updateMyNotificationSetting_missingChannel_badRequest() throws Exception {
        mockMvc.perform(put("/api/notification-settings/TASK_ASSIGNED")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inApp\":false}"))
                .andExpect(status().isBadRequest());

        verify(notificationSettingsService, never()).updateMySetting(any(), any(), any());
    }

    // --- cross-user isolation: the acted-on id comes only from the token ---

    @Test
    void updateMyNotificationSetting_bodyCarryingForeignIds_ignoresThemAndUsesTheToken() throws Exception {
        when(notificationSettingsService.updateMySetting(eq(7L), eq(NotificationType.MEMBER_JOINED), any()))
                .thenReturn(new NotificationSettingResponse(NotificationType.MEMBER_JOINED, false, true));

        // A client trying to retarget another user via the body: the request DTO has no id
        // fields at all, so these are dropped and the caller's own id is used.
        mockMvc.perform(put("/api/notification-settings/MEMBER_JOINED")
                        .with(as(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inApp\":false,\"email\":true,\"userId\":999,\"user\":{\"idUser\":999}}"))
                .andExpect(status().isOk());

        verify(notificationSettingsService).updateMySetting(eq(7L), eq(NotificationType.MEMBER_JOINED), any());
        verify(notificationSettingsService, never()).updateMySetting(eq(999L), any(), any());
    }

    // --- batch ---

    private static final String BATCH_BODY =
            "{\"settings\":[{\"type\":\"TASK_ASSIGNED\",\"inApp\":false,\"email\":true}]}";

    @Test
    void updateMyNotificationSettings_unauthenticated_denied() throws Exception {
        mockMvc.perform(put("/api/notification-settings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH_BODY))
                .andExpect(status().is4xxClientError());

        verify(notificationSettingsService, never()).updateMySettings(any(), any());
    }

    @Test
    void updateMyNotificationSettings_authenticated_returnsTheWholeMatrix() throws Exception {
        when(notificationSettingsService.updateMySettings(eq(1L), any())).thenReturn(List.of(
                new NotificationSettingResponse(NotificationType.TASK_ASSIGNED, false, true),
                new NotificationSettingResponse(NotificationType.TASK_COMPLETED, true, true)));

        mockMvc.perform(put("/api/notification-settings/batch")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.data[1].type").value("TASK_COMPLETED"));
    }

    @Test
    void updateMyNotificationSettings_batchPath_isNotTakenForANotificationType() throws Exception {
        when(notificationSettingsService.updateMySettings(eq(1L), any())).thenReturn(List.of());

        mockMvc.perform(put("/api/notification-settings/batch")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH_BODY))
                .andExpect(status().isOk());

        // The literal /batch wins over the /{type} template, so it never reaches the single-type
        // handler (which would reject "batch" as an unknown type).
        verify(notificationSettingsService).updateMySettings(eq(1L), any());
        verify(notificationSettingsService, never()).updateMySetting(any(), any(), any());
    }

    @Test
    void updateMyNotificationSettings_duplicateTypes_badRequest() throws Exception {
        mockMvc.perform(put("/api/notification-settings/batch")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":["
                                + "{\"type\":\"TASK_ASSIGNED\",\"inApp\":false,\"email\":true},"
                                + "{\"type\":\"TASK_ASSIGNED\",\"inApp\":true,\"email\":false}]}"))
                .andExpect(status().isBadRequest());

        verify(notificationSettingsService, never()).updateMySettings(any(), any());
    }

    @Test
    void updateMyNotificationSettings_emptyBatch_badRequest() throws Exception {
        mockMvc.perform(put("/api/notification-settings/batch")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":[]}"))
                .andExpect(status().isBadRequest());

        verify(notificationSettingsService, never()).updateMySettings(any(), any());
    }

    @Test
    void updateMyNotificationSettings_entryMissingChannel_badRequest() throws Exception {
        mockMvc.perform(put("/api/notification-settings/batch")
                        .with(as(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":[{\"type\":\"TASK_ASSIGNED\",\"inApp\":false}]}"))
                .andExpect(status().isBadRequest());

        verify(notificationSettingsService, never()).updateMySettings(any(), any());
    }
}
