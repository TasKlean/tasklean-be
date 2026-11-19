package com.tasklean.api.domain.task;

import com.tasklean.api.domain.groupmember.GroupMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentReleaseTest {

    @Mock
    private TaskRepository taskRepository;
    @InjectMocks
    private TaskAssignmentRelease taskAssignmentRelease;

    private GroupMember departing() {
        return GroupMember.builder().idGroupMember(6L).uid("gm-6").build();
    }

    private Task assignedTask(Long id) {
        return Task.builder().idTask(id).uid("tsk-" + id).status(TaskStatus.TODO)
                .assignedTo(departing()).build();
    }

    @Test
    void releaseFor_openTasks_handsThemBackToThePool() {
        when(taskRepository.findOpenAssignedTo(eq(6L), any())).thenReturn(List.of(assignedTask(1L)));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        taskAssignmentRelease.releaseFor(departing());

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().getAssignedTo()).isNull();
    }

    @Test
    void releaseFor_asksOnlyForWhatIsStillWaitingToBeDone() {
        when(taskRepository.findOpenAssignedTo(eq(6L), any())).thenReturn(List.of());

        taskAssignmentRelease.releaseFor(departing());

        // A finished or archived task keeps its assignee: there it records who did the work,
        // not who owes it.
        ArgumentCaptor<Collection<TaskStatus>> statuses = ArgumentCaptor.captor();
        verify(taskRepository).findOpenAssignedTo(eq(6L), statuses.capture());
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
    }

    @Test
    void releaseFor_nothingAssigned_savesNothing() {
        when(taskRepository.findOpenAssignedTo(eq(6L), any())).thenReturn(List.of());

        assertThat(taskAssignmentRelease.releaseFor(departing())).isZero();
        verify(taskRepository, never()).save(any());
    }

    @Test
    void releaseFor_severalTasks_releasesEveryOne() {
        when(taskRepository.findOpenAssignedTo(eq(6L), any()))
                .thenReturn(List.of(assignedTask(1L), assignedTask(2L), assignedTask(3L)));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(taskAssignmentRelease.releaseFor(departing())).isEqualTo(3);
        verify(taskRepository, times(3)).save(any(Task.class));
    }
}
