package com.tasklean.api.security;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.taskcompletion.TaskCompletion;
import com.tasklean.api.domain.taskcompletion.TaskCompletionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The rights over one entry in a chore's history. Reading one is a household matter; taking one
 * back belongs to whoever it is attributed to, or to the household's admin.
 */
@ExtendWith(MockitoExtension.class)
class TaskCompletionSecurityTest {

    @Mock
    private TaskCompletionRepository taskCompletionRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private TaskCompletionSecurity taskCompletionSecurity;

    private static final Long ENTRY_ID = 99L;
    private static final Long GROUP_ID = 10L;
    private static final Long CALLER_MEMBERSHIP_ID = 4L;

    private void entryAttributedTo(Long membershipId) {
        when(taskCompletionRepository.findWithTaskByIdTaskCompletion(ENTRY_ID)).thenReturn(Optional.of(TaskCompletion.builder()
                .idTaskCompletion(ENTRY_ID)
                .task(Task.builder().idTask(2L).group(Group.builder().idGroup(GROUP_ID).build()).build())
                .attributedTo(membershipId == null
                        ? null : GroupMember.builder().idGroupMember(membershipId).build())
                .build()));
    }

    @Test
    void canView_memberOfTheHousehold_isAllowed() {
        entryAttributedTo(9L);
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(true);

        assertThat(taskCompletionSecurity.canView(ENTRY_ID)).isTrue();
    }

    @Test
    void canView_outsider_isRefused() {
        entryAttributedTo(9L);
        when(groupSecurity.isMemberOfGroup(GROUP_ID)).thenReturn(false);

        assertThat(taskCompletionSecurity.canView(ENTRY_ID)).isFalse();
    }

    @Test
    void canView_entryThatDoesNotExist_isRefusedRatherThanThrowing() {
        when(taskCompletionRepository.findWithTaskByIdTaskCompletion(ENTRY_ID)).thenReturn(Optional.empty());

        assertThat(taskCompletionSecurity.canView(ENTRY_ID)).isFalse();
    }

    @Test
    void canUndo_itsOwnMember_isAllowed() {
        entryAttributedTo(CALLER_MEMBERSHIP_ID);
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);
        when(groupSecurity.callersMembershipId(GROUP_ID)).thenReturn(Optional.of(CALLER_MEMBERSHIP_ID));

        assertThat(taskCompletionSecurity.canUndo(ENTRY_ID)).isTrue();
    }

    @Test
    void canUndo_somebodyElsesEntry_isRefused() {
        entryAttributedTo(77L);
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);
        when(groupSecurity.callersMembershipId(GROUP_ID)).thenReturn(Optional.of(CALLER_MEMBERSHIP_ID));

        assertThat(taskCompletionSecurity.canUndo(ENTRY_ID)).isFalse();
    }

    @Test
    void canUndo_householdAdmin_isAllowedWithoutOwningIt() {
        entryAttributedTo(77L);
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        // Somebody has to be able to correct a record for a member who is no longer around.
        assertThat(taskCompletionSecurity.canUndo(ENTRY_ID)).isTrue();
    }

    @Test
    void canUndo_entryNobodyIsAttributedTo_isRefusedForAPlainMember() {
        entryAttributedTo(null);
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        // A missed round names nobody, so a null attribution must not read as "everybody's".
        assertThat(taskCompletionSecurity.canUndo(ENTRY_ID)).isFalse();
    }

    @Test
    void canUndo_callerWithNoMembership_isRefused() {
        entryAttributedTo(CALLER_MEMBERSHIP_ID);
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);
        when(groupSecurity.callersMembershipId(GROUP_ID)).thenReturn(Optional.empty());

        assertThat(taskCompletionSecurity.canUndo(ENTRY_ID)).isFalse();
    }
}
