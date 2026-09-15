package com.tasklean.api.domain.user;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.user.dto.UserResponse;
import com.tasklean.api.security.AccountSecurity;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link UserController}: platform-role checks, the role
 * hierarchy (SUPER_ADMIN → ADMIN → USER), self-access via {@code @accountSecurity}, and that a
 * denial maps to a 403 ApiResponse. Authentication is injected per-request; the filter chain is
 * permissive so only method security decides.
 */
@WebMvcTest(UserController.class)
@Import({MethodSecuritySliceConfig.class, AccountSecurity.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // The app's @Component JWT filter is pulled into the slice; mock its dependency so the
    // context builds. We authenticate per-request via a post-processor, not through this filter.
    @MockitoBean
    private JwtService jwtService;

    private RequestPostProcessor as(UserRole role, String uid) {
        AuthPrincipal principal = new AuthPrincipal(1L, uid, "user@example.com", role);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    // --- list all users: ADMIN only, with hierarchy ---

    @Test
    void getAllUsers_asUser_forbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(as(UserRole.USER, "usr-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAllUsers_asAdmin_ok() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/users").with(as(UserRole.ADMIN, "usr-1")))
                .andExpect(status().isOk());
    }

    @Test
    void getAllUsers_asSuperAdmin_ok() throws Exception {
        // Proves the hierarchy: SUPER_ADMIN satisfies hasRole('ADMIN').
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/users").with(as(UserRole.SUPER_ADMIN, "usr-1")))
                .andExpect(status().isOk());
    }

    // --- get one user: ADMIN or self ---

    @Test
    void getUser_self_ok() throws Exception {
        when(userService.getUserByUid("usr-1")).thenReturn(UserResponse.builder().uid("usr-1").build());

        mockMvc.perform(get("/api/users/usr-1").with(as(UserRole.USER, "usr-1")))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_otherUserAsPlainUser_forbidden() throws Exception {
        mockMvc.perform(get("/api/users/usr-2").with(as(UserRole.USER, "usr-1")))
                .andExpect(status().isForbidden());
    }

    // --- change role: SUPER_ADMIN only (ADMIN does NOT imply SUPER_ADMIN) ---

    @Test
    void updateUserRole_asAdmin_forbidden() throws Exception {
        mockMvc.perform(put("/api/users/usr-2/role")
                        .with(as(UserRole.ADMIN, "usr-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserRole_asSuperAdmin_ok() throws Exception {
        when(userService.updateUserRole("usr-2", UserRole.ADMIN))
                .thenReturn(UserResponse.builder().uid("usr-2").role(UserRole.ADMIN).build());

        mockMvc.perform(put("/api/users/usr-2/role")
                        .with(as(UserRole.SUPER_ADMIN, "usr-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }
}
