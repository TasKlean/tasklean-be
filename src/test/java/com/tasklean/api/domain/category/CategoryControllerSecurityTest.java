package com.tasklean.api.domain.category;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.CategorySecurity;
import com.tasklean.api.security.GroupSecurity;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link CategoryController}: every member reads, only a
 * household admin writes. CategorySecurity and GroupSecurity are real, their repositories mocked
 * to place the caller.
 */
@WebMvcTest(CategoryController.class)
@Import({MethodSecuritySliceConfig.class, CategorySecurity.class, GroupSecurity.class})
class CategoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    private static final String BODY = """
            {"name":"Kitchen","color":"#FF6B35","groupUid":"grp-1"}
            """;

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).isActive(true).build();
    }

    // Places the caller in the household in the given role.
    private void callerIs(GroupRole role) {
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .idGroupMember(4L).role(role).isActive(true)
                        .group(group()).user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    private void householdNamedInTheBodyExists() {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(group()));
    }

    private void categoryExists() {
        when(categoryRepository.findById(5L))
                .thenReturn(Optional.of(Category.builder().idCategory(5L).group(group()).build()));
    }

    @Test
    void createCategory_householdAdmin_created() throws Exception {
        householdNamedInTheBodyExists();
        callerIs(GroupRole.GROUP_ADMIN);
        when(categoryService.createCategory(any())).thenReturn(new CategoryResponse());

        mockMvc.perform(post("/api/categories").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void createCategory_plainMember_forbidden() throws Exception {
        householdNamedInTheBodyExists();
        callerIs(GroupRole.GROUP_MEMBER);

        mockMvc.perform(post("/api/categories").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_platformSuperAdminOutsideTheHousehold_forbidden() throws Exception {
        householdNamedInTheBodyExists();

        mockMvc.perform(post("/api/categories").with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCategory_plainMember_ok() throws Exception {
        categoryExists();
        callerIs(GroupRole.GROUP_MEMBER);
        when(categoryService.getCategoryById(5L)).thenReturn(new CategoryResponse());

        mockMvc.perform(get("/api/categories/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getCategory_callerNotInThatHousehold_forbidden() throws Exception {
        categoryExists();

        mockMvc.perform(get("/api/categories/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCategory_plainMember_forbidden() throws Exception {
        categoryExists();
        callerIs(GroupRole.GROUP_MEMBER);

        mockMvc.perform(put("/api/categories/{id}", 5L).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCategory_householdAdmin_ok() throws Exception {
        categoryExists();
        callerIs(GroupRole.GROUP_ADMIN);
        when(categoryService.updateCategory(any(), any())).thenReturn(new CategoryResponse());

        mockMvc.perform(put("/api/categories/{id}", 5L).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCategory_plainMember_forbidden() throws Exception {
        categoryExists();
        callerIs(GroupRole.GROUP_MEMBER);

        mockMvc.perform(delete("/api/categories/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCategory_householdAdmin_ok() throws Exception {
        categoryExists();
        callerIs(GroupRole.GROUP_ADMIN);

        mockMvc.perform(delete("/api/categories/{id}", 5L).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }
}
