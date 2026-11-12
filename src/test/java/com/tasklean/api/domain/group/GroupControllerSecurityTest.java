package com.tasklean.api.domain.group;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.dto.GroupResponse;
import com.tasklean.api.domain.group.dto.MyGroupResponse;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupMemberService;
import com.tasklean.api.domain.groupmember.GroupRole;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link GroupController}: group membership/role checks via
 * {@code @groupSecurity} and the platform-role fallback. GroupSecurity is real; its repositories
 * are mocked to place the caller inside (or outside) the target group.
 */
@WebMvcTest(GroupController.class)
@Import({MethodSecuritySliceConfig.class, GroupSecurity.class})
class GroupControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtService jwtService;

    // GroupController serves the group's own member collection, so the slice needs this too.
    @MockitoBean
    private GroupMemberService groupMemberService;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

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

    // --- joinGroup: any authenticated caller; holding a valid code is the authorization ---

    @Test
    void joinGroup_plainUserNotYetInGroup_ok() throws Exception {
        when(groupService.joinByInviteCode("JOIN1234", USER_ID))
                .thenReturn(MyGroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(post("/api/groups/join")
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"JOIN1234\"}"))
                .andExpect(status().isOk());
    }

    // --- getMyGroups: any authenticated caller, scoped to the token ---

    @Test
    void getMyGroups_plainUser_ok() throws Exception {
        when(groupService.getMyGroups(eq(USER_ID), any())).thenReturn(List.of());

        // Also guards route precedence: if the literal /mine lost to /{uid}, a non-member USER
        // would be denied by @groupSecurity.isMember("mine") instead of getting their groups.
        mockMvc.perform(get("/api/groups/mine").with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getMyGroups_searchOverTheLimit_rejectedAsBadRequest() throws Exception {
        // The bound is on an object rather than on a bare parameter precisely so this is a 400:
        // a constraint on the parameter itself would have surfaced as a 500.
        mockMvc.perform(get("/api/groups/mine").param("q", "x".repeat(101)).with(as(UserRole.USER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyGroups_unknownSort_rejectedAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/groups/mine").param("sort", "NOT_A_SORT").with(as(UserRole.USER)))
                .andExpect(status().isBadRequest());
    }

    // --- getGroup: member or platform ADMIN ---

    @Test
    void getGroup_nonMember_forbidden() throws Exception {
        // groupRepository.findByUid unstubbed → Optional.empty() → not a member
        mockMvc.perform(get("/api/groups/{uid}", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroup_member_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);
        when(groupService.getGroupByUid(GROUP_UID, USER_ID))
                .thenReturn(GroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(get("/api/groups/{uid}", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getGroup_platformAdmin_ok() throws Exception {
        when(groupService.getGroupByUid(GROUP_UID, USER_ID))
                .thenReturn(GroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(get("/api/groups/{uid}", GROUP_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    // --- getMembers: the group's own member collection, open to any member or platform ADMIN ---

    @Test
    void getMembers_nonMember_forbidden() throws Exception {
        mockMvc.perform(get("/api/groups/{uid}/members", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMembers_plainMember_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);
        when(groupMemberService.getMembers(eq(GROUP_UID), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/groups/{uid}/members", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getMembers_platformAdmin_ok() throws Exception {
        when(groupMemberService.getMembers(eq(GROUP_UID), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/groups/{uid}/members", GROUP_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void getMembers_nonMemberAskingForEveryone_forbidden() throws Exception {
        // Asking for the people who have left must not be a way past the gate - a former member
        // would otherwise read the roster of a group they are no longer in.
        mockMvc.perform(get("/api/groups/{uid}/members", GROUP_UID)
                        .param("status", "ALL")
                        .with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMembers_unknownStatus_isABadRequest() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);

        mockMvc.perform(get("/api/groups/{uid}/members", GROUP_UID)
                        .param("status", "EVERYBODY")
                        .with(as(UserRole.USER)))
                .andExpect(status().isBadRequest());
    }

    // --- rotateInviteCode: group admin or platform SUPER_ADMIN ---

    @Test
    void rotateInviteCode_plainMember_forbidden() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);

        mockMvc.perform(post("/api/groups/{uid}/invite-code", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rotateInviteCode_groupAdmin_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);
        when(groupService.rotateInviteCode(GROUP_UID))
                .thenReturn(GroupResponse.builder().uid(GROUP_UID).inviteCode("NEW00001").build());

        mockMvc.perform(post("/api/groups/{uid}/invite-code", GROUP_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void rotateInviteCode_platformAdmin_forbidden() throws Exception {
        // ADMIN is read-only across the platform; only SUPER_ADMIN overrides group roles.
        mockMvc.perform(post("/api/groups/{uid}/invite-code", GROUP_UID).with(as(UserRole.ADMIN)))
                .andExpect(status().isForbidden());
    }

    // --- updateGroup: group admin or platform SUPER_ADMIN ---

    @Test
    void updateGroup_plainMember_forbidden() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_MEMBER);

        mockMvc.perform(put("/api/groups/{uid}", GROUP_UID)
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGroup_groupAdmin_ok() throws Exception {
        callerIsMemberWithRole(GroupRole.GROUP_ADMIN);
        when(groupService.updateGroup(org.mockito.ArgumentMatchers.eq(GROUP_UID),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(GroupResponse.builder().uid(GROUP_UID).build());

        mockMvc.perform(put("/api/groups/{uid}", GROUP_UID)
                        .with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\"}"))
                .andExpect(status().isOk());
    }
}
