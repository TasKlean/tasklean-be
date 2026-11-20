package com.tasklean.api.domain.task;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.task.dto.TaskResponse;
import com.tasklean.api.domain.taskcompletion.TaskCompletionService;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryPage;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import com.tasklean.api.security.MethodSecuritySliceConfig;
import com.tasklean.api.security.TaskSecurity;
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
 * Verifies the @PreAuthorize wiring on {@link TaskController}. A task's household is its boundary,
 * so the expressions resolve the group behind the task and then ask the caller's standing in it —
 * TaskSecurity and GroupSecurity are real, their repositories mocked to place the caller.
 */
@WebMvcTest(TaskController.class)
@Import({MethodSecuritySliceConfig.class, TaskSecurity.class, GroupSecurity.class})
class TaskControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private TaskCompletionService taskCompletionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private GroupMemberRepository groupMemberRepository;

    private static final String TASK_UID = "tsk-1";
    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final Long CALLER_MEMBERSHIP_ID = 4L;

    private static final String BODY = """
            {"name":"Clean","priority":"MEDIUM","groupUid":"grp-1"}
            """;

    private RequestPostProcessor as(UserRole platformRole) {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "usr-1", "user@example.com", platformRole);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()))));
    }

    // Places the task in a household, authored by authorMembershipId, with the caller a member of
    // that same household in the given group role.
    private void taskAuthoredBy(Long authorMembershipId, GroupRole callerRole) {
        Group group = Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).isActive(true).build();
        when(taskRepository.findByUid(TASK_UID)).thenReturn(Optional.of(Task.builder()
                .idTask(1L).uid(TASK_UID).group(group)
                .createdBy(GroupMember.builder().idGroupMember(authorMembershipId).build())
                .build()));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .idGroupMember(CALLER_MEMBERSHIP_ID).role(callerRole).isActive(true)
                        .group(group).user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    private void callerIsInTheHousehold(GroupRole role) {
        Group group = Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).isActive(true).build();
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByUserIdUserAndGroupIdGroup(USER_ID, GROUP_ID))
                .thenReturn(Optional.of(GroupMember.builder()
                        .idGroupMember(CALLER_MEMBERSHIP_ID).role(role).isActive(true)
                        .group(group).user(User.builder().idUser(USER_ID).build())
                        .build()));
    }

    // --- create: any member of the household named in the body ---

    @Test
    void createTask_plainMemberOfThatHousehold_created() throws Exception {
        callerIsInTheHousehold(GroupRole.GROUP_MEMBER);
        when(taskService.createTask(any(), any())).thenReturn(new TaskResponse());

        mockMvc.perform(post("/api/tasks").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void createTask_callerNotInThatHousehold_forbidden() throws Exception {
        // findByUid unstubbed → the group cannot be resolved, so the caller is nobody there
        mockMvc.perform(post("/api/tasks").with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTask_platformAdminOutsideTheHousehold_forbidden() throws Exception {
        // A platform ADMIN is not a member of anybody's household, and adding chores to one is a
        // member's act, not an administrative one.
        mockMvc.perform(post("/api/tasks").with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    // --- read: any member of the task's household ---

    @Test
    void getTask_plainMemberOfThatHousehold_ok() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);
        when(taskService.getTaskByUid(TASK_UID)).thenReturn(new TaskResponse());

        mockMvc.perform(get("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getTask_callerNotInThatHousehold_forbidden() throws Exception {
        mockMvc.perform(get("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    // --- write: the author or an admin of the task's household ---

    @Test
    void updateTask_theAuthor_ok() throws Exception {
        taskAuthoredBy(CALLER_MEMBERSHIP_ID, GroupRole.GROUP_MEMBER);
        when(taskService.updateTask(any(), any())).thenReturn(new TaskResponse());

        mockMvc.perform(put("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void updateTask_anotherMembersTask_forbidden() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        mockMvc.perform(put("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTask_householdAdmin_ok() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_ADMIN);
        when(taskService.updateTask(any(), any())).thenReturn(new TaskResponse());

        mockMvc.perform(put("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void archiveTask_anotherMembersTask_forbidden() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        mockMvc.perform(post("/api/tasks/{uid}/archive", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveTask_householdAdmin_ok() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_ADMIN);
        when(taskService.archiveTask(TASK_UID)).thenReturn(new TaskResponse());

        mockMvc.perform(post("/api/tasks/{uid}/archive", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteTask_anotherMembersTask_forbidden() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        mockMvc.perform(delete("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTask_theAuthor_ok() throws Exception {
        taskAuthoredBy(CALLER_MEMBERSHIP_ID, GroupRole.GROUP_MEMBER);

        mockMvc.perform(delete("/api/tasks/{uid}", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteTask_platformSuperAdminOutsideTheHousehold_forbidden() throws Exception {
        // A task belongs to a household, so no platform role reaches it from outside one.
        mockMvc.perform(delete("/api/tasks/{uid}", TASK_UID).with(as(UserRole.SUPER_ADMIN)))
                .andExpect(status().isForbidden());
    }

    // --- doing it: any member of the household; whose chore it is, the service decides ---

    @Test
    void completeTask_plainMemberOfThatHousehold_created() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);
        when(taskCompletionService.complete(any(), any(), any())).thenReturn(new TaskCompletionResponse());

        // Membership is the coarse right, which is all an expression can state. A chore assigned to
        // somebody else is refused by TaskCompletionService, not here.
        mockMvc.perform(post("/api/tasks/{uid}/complete", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void completeTask_callerNotInThatHousehold_forbidden() throws Exception {
        mockMvc.perform(post("/api/tasks/{uid}/complete", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeTask_platformAdminOutsideTheHousehold_forbidden() throws Exception {
        mockMvc.perform(post("/api/tasks/{uid}/complete", TASK_UID).with(as(UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeTask_overLongNote_isBadRequest() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        mockMvc.perform(post("/api/tasks/{uid}/complete", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeTask_absurdMinutes_isBadRequest() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        mockMvc.perform(post("/api/tasks/{uid}/complete", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":99999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void skipTask_plainMemberOfThatHousehold_created() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);
        when(taskCompletionService.skip(any(), any(), any())).thenReturn(new TaskCompletionResponse());

        mockMvc.perform(post("/api/tasks/{uid}/skip", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void skipTask_callerNotInThatHousehold_forbidden() throws Exception {
        mockMvc.perform(post("/api/tasks/{uid}/skip", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // --- claiming: any member, since volunteering needs no right over the task ---

    @Test
    void claimTask_plainMemberOfThatHousehold_ok() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);
        when(taskService.claimTask(any(), any())).thenReturn(new TaskResponse());

        mockMvc.perform(post("/api/tasks/{uid}/claim", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void claimTask_callerNotInThatHousehold_forbidden() throws Exception {
        mockMvc.perform(post("/api/tasks/{uid}/claim", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    // --- handing it on: the author or a household admin, not any member ---

    @Test
    void assignTask_plainMemberWhoDidNotCreateIt_forbidden() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        // Deciding who does a chore is not the same as doing it, which is why this one endpoint on
        // the chore is gated harder than completing it.
        mockMvc.perform(put("/api/tasks/{uid}/assignee", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignTask_itsAuthor_ok() throws Exception {
        taskAuthoredBy(CALLER_MEMBERSHIP_ID, GroupRole.GROUP_MEMBER);
        when(taskService.assignTask(any(), any())).thenReturn(new TaskResponse());

        mockMvc.perform(put("/api/tasks/{uid}/assignee", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void assignTask_householdAdmin_ok() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_ADMIN);
        when(taskService.assignTask(any(), any())).thenReturn(new TaskResponse());

        mockMvc.perform(put("/api/tasks/{uid}/assignee", TASK_UID).with(as(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    // --- history: any member of the household ---

    @Test
    void getTaskHistory_plainMemberOfThatHousehold_ok() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);
        when(taskCompletionService.getHistory(any(), any())).thenReturn(new TaskHistoryPage());

        mockMvc.perform(get("/api/tasks/{uid}/completions", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void getTaskHistory_callerNotInThatHousehold_forbidden() throws Exception {
        mockMvc.perform(get("/api/tasks/{uid}/completions", TASK_UID).with(as(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTaskHistory_absurdLimit_isBadRequest() throws Exception {
        taskAuthoredBy(9L, GroupRole.GROUP_MEMBER);

        mockMvc.perform(get("/api/tasks/{uid}/completions", TASK_UID).param("limit", "5000")
                        .with(as(UserRole.USER)))
                .andExpect(status().isBadRequest());
    }
}
