package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.Category;
import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.dto.TaskRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private TaskService taskService;

    private static final Long GROUP_ID = 10L;
    private static final Long CREATOR_ID = 5L;

    private Group group() {
        return Group.builder().idGroup(GROUP_ID).build();
    }

    private GroupMember member(Long id) {
        return GroupMember.builder().idGroupMember(id).group(group()).build();
    }

    private TaskRequest request() {
        TaskRequest r = new TaskRequest();
        r.setName("Clean");
        r.setGroupId(GROUP_ID);
        r.setCreatedById(CREATOR_ID);
        return r;
    }

    private Task task() {
        return Task.builder().idTask(1L).uid("tsk-1").name("Clean").isActive(true)
                .group(group()).createdBy(member(CREATOR_ID)).build();
    }

    @Test
    void getTaskByUid_found_returns() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        assertThat(taskService.getTaskByUid("tsk-1").getUid()).isEqualTo("tsk-1");
    }

    @Test
    void getTaskByUid_missing_throws() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.getTaskByUid("tsk-1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTasksByGroup_returnsList() {
        when(taskRepository.findByGroupIdGroupAndIsActiveTrue(GROUP_ID)).thenReturn(List.of(task()));
        assertThat(taskService.getTasksByGroup(GROUP_ID)).hasSize(1);
    }

    @Test
    void createTask_minimal_success() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findById(CREATOR_ID)).thenReturn(Optional.of(member(CREATOR_ID)));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> {
            Task t = i.getArgument(0);
            t.setIdTask(1L);
            return t;
        });

        assertThat(taskService.createTask(request()).getName()).isEqualTo("Clean");
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), eq(1L), eq(AuditAction.CREATE), any(), any(Group.class));
    }

    @Test
    void createTask_withAssigneeAndCategory_success() {
        TaskRequest r = request();
        r.setAssignedToId(6L);
        r.setCategoryId(3L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findById(CREATOR_ID)).thenReturn(Optional.of(member(CREATOR_ID)));
        when(groupMemberRepository.findById(6L)).thenReturn(Optional.of(member(6L)));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(Category.builder().idCategory(3L).group(group()).build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> {
            Task t = i.getArgument(0);
            t.setIdTask(1L);
            return t;
        });

        assertThat(taskService.createTask(r).getAssignedToId()).isEqualTo(6L);
    }

    @Test
    void createTask_groupMissing_throws() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.createTask(request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_creatorMissing_throws() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findById(CREATOR_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.createTask(request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTask_assigneeMissing_throws() {
        TaskRequest r = request();
        r.setAssignedToId(6L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(groupMemberRepository.findById(CREATOR_ID)).thenReturn(Optional.of(member(CREATOR_ID)));
        when(groupMemberRepository.findById(6L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.createTask(r)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTask_success_clearsAssigneeAndCategory() {
        Task existing = task();
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        taskService.updateTask("tsk-1", request());

        assertThat(existing.getAssignedTo()).isNull();
        assertThat(existing.getCategory()).isNull();
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), eq(1L), eq(AuditAction.UPDATE), any(), any());
    }

    @Test
    void updateTask_withAssigneeAndCategory() {
        TaskRequest r = request();
        r.setAssignedToId(6L);
        r.setCategoryId(3L);
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupMemberRepository.findById(6L)).thenReturn(Optional.of(member(6L)));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(Category.builder().idCategory(3L).group(group()).build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(taskService.updateTask("tsk-1", r).getCategoryId()).isEqualTo(3L);
    }

    @Test
    void updateTask_missing_throws() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.updateTask("tsk-1", request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTask_success() {
        Task existing = task();
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(existing));

        taskService.deleteTask("tsk-1");

        assertThat(existing.getIsActive()).isFalse();
        verify(auditLogService).recordEvent(eq(AuditEntityType.TASK), eq(1L), eq(AuditAction.DELETE), any(), any());
    }

    @Test
    void deleteTask_missing_throws() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.deleteTask("tsk-1")).isInstanceOf(ResourceNotFoundException.class);
    }
}
