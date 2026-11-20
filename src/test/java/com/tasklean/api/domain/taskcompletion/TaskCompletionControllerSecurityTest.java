package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import com.tasklean.api.security.TaskCompletionSecurity;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the @PreAuthorize wiring on {@link TaskCompletionController}. An entry belongs to a task
 * and so to a household: reading one needs membership, taking one back needs it to be the caller's
 * own or them to administer the household. TaskCompletionSecurity and GroupSecurity are real.
 */
@WebMvcTest(TaskCompletionController.class)
@Import({MethodSecuritySliceConfig.class, TaskCompletionSecurity.class, GroupSecurity.class})
class TaskCompletionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskCompletionService taskCompletionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private TaskCompletionRepository taskCompletionRepository;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final Long ENTRY_ID = 99L;
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final Long CALLER_MEMBERSHIP_ID = 4L;

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    // Places the entry in a household, attributed to attributedMembershipId, with the caller a
    // member of that same household in the given group role.
    private void entryAttributedTo(Long attributedMembershipId, GroupRole callerRole) {
        Group group = Group.builder().idGroup(GROUP_ID).uid("grp-1").isActive(true).build();
        when(taskCompletionRepository.findWithTaskByIdTaskCompletion(ENTRY_ID)).thenReturn(Optional.of(TaskCompletion.builder()
                .idTaskCompletion(ENTRY_ID).isActive(true)
                .task(Task.builder().idTask(2L).uid("tsk-1").group(group).build())
                .attributedTo(GroupMember.builder().idGroupMember(attributedMembershipId).build())
                .build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .idGroupMember(CALLER_MEMBERSHIP_ID).role(callerRole).isActive(true)
                        .group(group).user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    @Test
    void getCompletion_plainMemberOfThatHousehold_ok() throws Exception {
        entryAttributedTo(9L, GroupRole.GROUP_MEMBER);
        when(taskCompletionService.getCompletion(ENTRY_ID)).thenReturn(new TaskCompletionResponse());

        // A household's history is the household's business, as its trail is - so reading somebody
        // else's entry is fine, and only changing one is not.
        mockMvc.perform(get("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getCompletion_callerNotInThatHousehold_forbidden() throws Exception {
        mockMvc.perform(get("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompletion_platformAdminOutsideTheHousehold_forbidden() throws Exception {
        mockMvc.perform(get("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.SUPER_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void undoCompletion_itsOwnMember_ok() throws Exception {
        entryAttributedTo(CALLER_MEMBERSHIP_ID, GroupRole.GROUP_MEMBER);
        when(taskCompletionService.undo(any())).thenReturn(new TaskCompletionResponse());

        mockMvc.perform(delete("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void undoCompletion_somebodyElsesEntry_forbidden() throws Exception {
        entryAttributedTo(9L, GroupRole.GROUP_MEMBER);

        // It says somebody did something; only they, or the household's admin, may say otherwise.
        mockMvc.perform(delete("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void undoCompletion_householdAdmin_ok() throws Exception {
        entryAttributedTo(9L, GroupRole.GROUP_ADMIN);
        when(taskCompletionService.undo(any())).thenReturn(new TaskCompletionResponse());

        // Somebody has to be able to correct a record for a member who is no longer around to
        // correct it themselves.
        mockMvc.perform(delete("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void undoCompletion_entryNobodyIsAttributedTo_forbiddenForAPlainMember() throws Exception {
        Group group = Group.builder().idGroup(GROUP_ID).uid("grp-1").isActive(true).build();
        when(taskCompletionRepository.findWithTaskByIdTaskCompletion(ENTRY_ID)).thenReturn(Optional.of(TaskCompletion.builder()
                .idTaskCompletion(ENTRY_ID).isActive(true)
                .task(Task.builder().idTask(2L).uid("tsk-1").group(group).build())
                .build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .idGroupMember(CALLER_MEMBERSHIP_ID).role(GroupRole.GROUP_MEMBER).isActive(true)
                        .group(group).user(User.builder().idUser(USER_ID).build())
                        .build()));

        // A missed round names nobody, so there is no "own entry" to claim - it is the admin's to
        // correct, and a null attribution must not read as "everybody's".
        mockMvc.perform(delete("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void undoCompletion_entryThatDoesNotExist_forbidden() throws Exception {
        mockMvc.perform(delete("/api/task-completions/{id}", ENTRY_ID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }
}
