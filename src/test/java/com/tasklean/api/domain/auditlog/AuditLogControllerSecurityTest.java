package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link AuditLogController}. The trail is other people's
 * history, so every endpoint had to stop answering an arbitrary signed-in caller: the group feed
 * is for that group's admins, and the two id-addressed reads for platform ADMIN only.
 * GroupSecurity is real; its repositories are mocked to place the caller inside the target group.
 */
@WebMvcTest(AuditLogController.class)
@Import({MethodSecuritySliceConfig.class, GroupSecurity.class})
class AuditLogControllerSecurityTest {

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    private void callerIsMemberWithRole(GroupRole role) {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .role(role).isActive(true)
                        .group(Group.builder().idGroup(GROUP_ID).build())
                        .user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    // --- group feed: that group's admins, or a platform ADMIN reading across households ---

    @Test
    void getLogsByGroup_unauthenticated_denied() throws Exception {
        mockMvc.perform(get("/api/audit-logs?groupId=" + GROUP_ID))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getLogsByGroup_callerNotInTheGroup_denied() throws Exception {
        // The bug this closes: any signed-in user could read any household's trail.
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/audit-logs?groupId=" + GROUP_ID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogsByGroup_plainMemberOfTheGroup_denied() throws Exception {
        // Strict for now on purpose: a member's own slice needs row-level narrowing, which the
        // scoped endpoints bring. Until then the whole feed is admin-only rather than over-shared.
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);

        mockMvc.perform(get("/api/audit-logs?groupId=" + GROUP_ID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogsByGroup_groupAdmin_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);
        when(auditLogService.getLogsByGroup(GROUP_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit-logs?groupId=" + GROUP_ID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getLogsByGroup_platformAdminNotInTheGroup_ok() throws Exception {
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID)).thenReturn(Optional.empty());
        when(auditLogService.getLogsByGroup(GROUP_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit-logs?groupId=" + GROUP_ID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    // --- entity and member reads: platform ADMIN only ---

    @Test
    void getLogsByEntity_plainUser_denied() throws Exception {
        // This one answered entityType=USER for anybody, i.e. another person's sign-in history.
        mockMvc.perform(get("/api/audit-logs/entity?entityType=USER&entityId=5").with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogsByEntity_platformAdmin_ok() throws Exception {
        when(auditLogService.getLogsByEntity("USER", 5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit-logs/entity?entityType=USER&entityId=5").with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void getLogsByMember_groupAdminOfThatMembersGroup_denied() throws Exception {
        // Even a household admin: the path carries an internal membership id, so there is no uid
        // for @groupSecurity to resolve the owning group from.
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);

        mockMvc.perform(get("/api/audit-logs/member/7").with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogsByMember_platformAdmin_ok() throws Exception {
        when(auditLogService.getLogsByMember(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit-logs/member/7").with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }
}
