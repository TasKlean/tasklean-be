package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEntityRefsTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditEntityRefs auditEntityRefs;

    private AuditLog entry(String entityType, Long entityId) {
        return AuditLog.builder().entityType(entityType).entityId(entityId).build();
    }

    @Test
    void resolve_entriesOfOneType_asksForEveryIdInOneQuery() {
        when(taskRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(
                Task.builder().idTask(1L).uid("tsk-1").build(),
                Task.builder().idTask(2L).uid("tsk-2").build()));

        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(
                List.of(entry(AuditEntityType.TASK, 1L), entry(AuditEntityType.TASK, 2L)));

        assertThat(refs).containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.TASK, 1L), "tsk-1")
                .containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.TASK, 2L), "tsk-2");
    }

    @Test
    void resolve_mixedTypes_queriesEachTypeOnceAndLeavesTheOthersAlone() {
        // A feed mixes types, which is the whole reason resolution is batched per type.
        when(taskRepository.findAllById(Set.of(1L))).thenReturn(List.of(Task.builder().idTask(1L).uid("tsk-1").build()));
        when(groupRepository.findAllById(Set.of(7L))).thenReturn(List.of(Group.builder().idGroup(7L).uid("grp-7").build()));

        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(
                List.of(entry(AuditEntityType.TASK, 1L), entry(AuditEntityType.GROUP, 7L)));

        assertThat(refs).hasSize(2);
        verify(groupMemberRepository, never()).findAllById(any());
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void resolve_membershipAndUserEntries_useTheirUids() {
        when(groupMemberRepository.findAllById(Set.of(5L)))
                .thenReturn(List.of(GroupMember.builder().idGroupMember(5L).uid("mem-5").build()));
        when(userRepository.findAllById(Set.of(3L)))
                .thenReturn(List.of(User.builder().idUser(3L).uid("usr-3").build()));

        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(
                List.of(entry(AuditEntityType.GROUP_MEMBER, 5L), entry(AuditEntityType.USER, 3L)));

        assertThat(refs).containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.GROUP_MEMBER, 5L), "mem-5")
                .containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.USER, 3L), "usr-3");
    }

    @Test
    void resolve_idAddressedTypes_needNoLookup() {
        // Category, Tag and TaskCompletion are addressed by their Long id everywhere in the API,
        // so that id already is the external reference - a query would answer its own question.
        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(List.of(
                entry(AuditEntityType.CATEGORY, 4L),
                entry(AuditEntityType.TAG, 8L),
                entry(AuditEntityType.TASK_COMPLETION, 12L)));

        assertThat(refs).containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.CATEGORY, 4L), "4")
                .containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.TAG, 8L), "8")
                .containsEntry(new AuditEntityRefs.EntityKey(AuditEntityType.TASK_COMPLETION, 12L), "12");
        verify(taskRepository, never()).findAllById(any());
    }

    @Test
    void resolve_targetThatNoLongerExists_isAbsentRatherThanFailing() {
        // The trail outlives what it describes - it is append-only and nothing it points at is
        // guaranteed to still be there. A missing target renders as no reference.
        when(taskRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        Map<AuditEntityRefs.EntityKey, String> refs = auditEntityRefs.resolve(List.of(entry(AuditEntityType.TASK, 99L)));

        assertThat(refs).isEmpty();
    }

    @Test
    void resolve_noEntries_queriesNothing() {
        assertThat(auditEntityRefs.resolve(List.of())).isEmpty();

        verify(taskRepository, never()).findAllById(any());
        verify(groupRepository, never()).findAllById(any());
    }
}
