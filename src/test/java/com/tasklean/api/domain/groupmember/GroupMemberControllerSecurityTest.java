package com.tasklean.api.domain.groupmember;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.dto.GroupMemberResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
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
 * Verifies the @PreAuthorize wiring on {@link GroupMemberController}. Each endpoint addresses a
 * membership by its own UID, so the expressions resolve the membership's group first and then ask
 * the caller's role in it — GroupSecurity is real, its repositories mocked to place the caller.
 */
@WebMvcTest(GroupMemberController.class)
@Import({MethodSecuritySliceConfig.class, GroupSecurity.class})
class GroupMemberControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupMemberService groupMemberService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final String MEMBER_UID = "mem-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    // Places the target membership in a group, and the caller in that same group with the given role.
    private void targetMemberWithCallerAs(GroupRole callerRole, Long targetUserId) {
        Group group = Group.builder().idGroup(GROUP_ID).build();
        when(groupMemberRepository.findByUid(MEMBER_UID)).thenReturn(Optional.of(GroupMember.builder()
                .uid(MEMBER_UID).role(GroupRole.GROUP_MEMBER).isActive(true)
                .group(group).user(User.builder().idUser(targetUserId).build())
                .build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .role(callerRole).isActive(true)
                        .group(group).user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    // --- read: any member of the membership's group, or a platform ADMIN ---

    @Test
    void getMember_callerNotInThatGroup_forbidden() throws Exception {
        // findByUid unstubbed → Optional.empty() → the group cannot be resolved, so no access
        mockMvc.perform(get("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMember_plainMemberOfThatGroup_ok() throws Exception {
        targetMemberWithCallerAs(GroupRole.GROUP_MEMBER, 2L);
        when(groupMemberService.getMember(MEMBER_UID))
                .thenReturn(GroupMemberResponse.builder().uid(MEMBER_UID).build());

        mockMvc.perform(get("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getMember_platformAdmin_ok() throws Exception {
        when(groupMemberService.getMember(MEMBER_UID))
                .thenReturn(GroupMemberResponse.builder().uid(MEMBER_UID).build());

        mockMvc.perform(get("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    // --- addMember: platform SUPER_ADMIN only ---

    @Test
    void addMember_groupAdmin_forbidden() throws Exception {
        // A household admin invites by sharing the code; adding someone outright is platform tooling.
        targetMemberWithCallerAs(GroupRole.GROUP_ADMIN, 2L);

        mockMvc.perform(post("/api/group-members")
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupUid\":\"grp-1\",\"userUid\":\"usr-2\",\"role\":\"GROUP_MEMBER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMember_platformAdmin_forbidden() throws Exception {
        // ADMIN is read-only across the platform.
        mockMvc.perform(post("/api/group-members")
                        .with(as(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupUid\":\"grp-1\",\"userUid\":\"usr-2\",\"role\":\"GROUP_MEMBER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMember_superAdmin_created() throws Exception {
        when(groupMemberService.addMember(any()))
                .thenReturn(GroupMemberResponse.builder().uid(MEMBER_UID).build());

        mockMvc.perform(post("/api/group-members")
                        .with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupUid\":\"grp-1\",\"userUid\":\"usr-2\",\"role\":\"GROUP_MEMBER\"}"))
                .andExpect(status().isCreated());
    }

    // --- role change: the group's admin or platform SUPER_ADMIN ---

    @Test
    void updateRole_plainMember_forbidden() throws Exception {
        targetMemberWithCallerAs(GroupRole.GROUP_MEMBER, 2L);

        mockMvc.perform(patch("/api/group-members/{memberUid}/role", MEMBER_UID)
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"GROUP_ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_groupAdmin_ok() throws Exception {
        targetMemberWithCallerAs(GroupRole.GROUP_ADMIN, 2L);
        when(groupMemberService.updateMemberRole(MEMBER_UID, GroupRole.GROUP_ADMIN))
                .thenReturn(GroupMemberResponse.builder().uid(MEMBER_UID).build());

        mockMvc.perform(patch("/api/group-members/{memberUid}/role", MEMBER_UID)
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"GROUP_ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateRole_platformAdmin_forbidden() throws Exception {
        mockMvc.perform(patch("/api/group-members/{memberUid}/role", MEMBER_UID)
                        .with(as(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"GROUP_ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    // --- removal: the group's admin, SUPER_ADMIN, or the member themselves (leaving) ---

    @Test
    void removeMember_someoneElsesMembershipAsAPlainMember_forbidden() throws Exception {
        targetMemberWithCallerAs(GroupRole.GROUP_MEMBER, 2L);

        mockMvc.perform(delete("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_groupAdmin_ok() throws Exception {
        targetMemberWithCallerAs(GroupRole.GROUP_ADMIN, 2L);

        mockMvc.perform(delete("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void removeMember_ownMembershipAsAPlainMember_ok() throws Exception {
        // Leaving: isSelfMember carries it, so a plain member needs no admin role for their own row.
        targetMemberWithCallerAs(GroupRole.GROUP_MEMBER, USER_ID);

        mockMvc.perform(delete("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void removeMember_callerNotInThatGroup_forbidden() throws Exception {
        mockMvc.perform(delete("/api/group-members/{memberUid}", MEMBER_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }
}
