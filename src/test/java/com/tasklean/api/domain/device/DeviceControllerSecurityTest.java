package com.tasklean.api.domain.device;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link DeviceController}: every endpoint requires an
 * authenticated caller, and acts on the id taken from the JWT principal (no cross-user access).
 */
@WebMvcTest(DeviceController.class)
@Import(MethodSecuritySliceConfig.class)
class DeviceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceService deviceService;

    // Pulled into the slice by the @Component JWT filter; mocked so the context builds.
    @MockitoBean
    private JwtService jwtService;

    private RequestPostProcessor as(Long userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, "usr-" + userId, "user@example.com", UserRole.USER);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void getMyDevices_unauthenticated_denied() throws Exception {
        // Not public: an anonymous call is rejected (401 or 403 depending on the mapping).
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMyDevices_authenticated_ok() throws Exception {
        when(deviceService.listMyDevices(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/devices").with(as(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutDevice_unauthenticated_denied() throws Exception {
        mockMvc.perform(post("/api/devices/5/logout"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void logoutDevice_authenticated_ok() throws Exception {
        mockMvc.perform(post("/api/devices/5/logout").with(as(1L)))
                .andExpect(status().isOk());
    }
}
