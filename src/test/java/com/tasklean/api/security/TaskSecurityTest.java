package com.tasklean.api.security;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskSecurityTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private TaskSecurity taskSecurity;

    private static final Long GROUP_ID = 10L;
    private static final Long AUTHOR_ID = 5L;

    private Task task() {
        return Task.builder().idTask(1L).uid("tsk-1")
                .group(Group.builder().idGroup(GROUP_ID).build())
                .createdBy(GroupMember.builder().idGroupMember(AUTHOR_ID).build())
                .build();
    }

    @Test
    void canView_memberOfTheHousehold_isTrue() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(taskSecurity.canView("tsk-1")).isTrue();
    }

    @Test
    void canView_outsider_isFalse() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(false);

        assertThat(taskSecurity.canView("tsk-1")).isFalse();
    }

    @Test
    void canView_noSuchTask_isFalse() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.empty());

        assertThat(taskSecurity.canView("tsk-1")).isFalse();
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void canView_softDeletedTask_stillResolves() {
        // Deliberately unfiltered: the member reaches the service and gets a 404, while an
        // outsider is still stopped here with a 403. Filtering would collapse the two.
        Task deleted = task();
        deleted.setIsActive(false);
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(deleted));
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(taskSecurity.canView("tsk-1")).isTrue();
    }

    @Test
    void canEdit_theAuthor_isTrue() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);
        when(groupSecurity.callersMembershipId(GROUP_ID)).thenReturn(Optional.of(AUTHOR_ID));

        assertThat(taskSecurity.canEdit("tsk-1")).isTrue();
    }

    @Test
    void canEdit_groupAdmin_isTrue() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        // An admin may tidy up after a member who lost interest in a chore they created, so
        // authorship is not even consulted.
        assertThat(taskSecurity.canEdit("tsk-1")).isTrue();
        verify(groupSecurity, never()).callersMembershipId(any());
    }

    @Test
    void canEdit_anotherMember_isFalse() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);
        when(groupSecurity.callersMembershipId(GROUP_ID)).thenReturn(Optional.of(7L));

        assertThat(taskSecurity.canEdit("tsk-1")).isFalse();
    }

    @Test
    void canEdit_outsider_isFalse() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.of(task()));
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);
        when(groupSecurity.callersMembershipId(GROUP_ID)).thenReturn(Optional.empty());

        assertThat(taskSecurity.canEdit("tsk-1")).isFalse();
    }

    @Test
    void canEdit_noSuchTask_isFalse() {
        when(taskRepository.findByUid("tsk-1")).thenReturn(Optional.empty());

        assertThat(taskSecurity.canEdit("tsk-1")).isFalse();
    }
}
