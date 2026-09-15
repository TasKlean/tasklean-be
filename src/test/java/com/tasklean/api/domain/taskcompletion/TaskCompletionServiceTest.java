package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskCompletionServiceTest {

    @Mock
    private TaskCompletionRepository taskCompletionRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private Clock clock = Clock.systemUTC();
    @InjectMocks
    private TaskCompletionService taskCompletionService;

    private static final Long TASK_ID = 2L;
    private static final Long MEMBER_ID = 5L;

    private Task task() {
        return Task.builder().idTask(TASK_ID).name("Clean").group(Group.builder().idGroup(10L).build()).build();
    }

    private GroupMember member() {
        return GroupMember.builder().idGroupMember(MEMBER_ID).build();
    }

    private TaskCompletionRequest request() {
        TaskCompletionRequest r = new TaskCompletionRequest();
        r.setTaskId(TASK_ID);
        r.setGroupMemberId(MEMBER_ID);
        r.setCompletionNote("done");
        return r;
    }

    private TaskCompletion completion() {
        return TaskCompletion.builder().idTaskCompletion(1L).task(task()).completedBy(member()).build();
    }

    @Test
    void getCompletionsByTask_returnsList() {
        when(taskCompletionRepository.findByTaskIdTaskOrderByDateCompletedDesc(TASK_ID)).thenReturn(List.of(completion()));
        assertThat(taskCompletionService.getCompletionsByTask(TASK_ID)).hasSize(1);
    }

    @Test
    void getCompletionsByMember_returnsList() {
        when(taskCompletionRepository.findByCompletedByIdGroupMemberOrderByDateCompletedDesc(MEMBER_ID))
                .thenReturn(List.of(completion()));
        assertThat(taskCompletionService.getCompletionsByMember(MEMBER_ID)).hasSize(1);
    }

    @Test
    void createCompletion_success_savesAndAudits() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
        when(taskCompletionRepository.save(any(TaskCompletion.class))).thenAnswer(i -> {
            TaskCompletion c = i.getArgument(0);
            c.setIdTaskCompletion(1L);
            return c;
        });

        assertThat(taskCompletionService.createCompletion(request()).getTaskId()).isEqualTo(TASK_ID);
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK_COMPLETION), eq(1L), eq(AuditAction.COMPLETE), any(), any(Group.class));
    }

    @Test
    void createCompletion_taskMissing_throws() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskCompletionService.createCompletion(request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCompletion_memberMissing_throws() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskCompletionService.createCompletion(request())).isInstanceOf(ResourceNotFoundException.class);
    }
}
