package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditContext auditContext;

    @InjectMocks
    private AuditLogService auditLogService;

    // --- record ---

    @Test
    void record_withGroup_resolvesAndStampsActingMember() {
        Group group = Group.builder().idGroup(10L).build();
        User actorUser = User.builder().idUser(1L).build();
        GroupMember actor = GroupMember.builder().idGroupMember(5L).build();

        when(auditContext.currentUser()).thenReturn(actorUser);
        when(auditContext.currentActor(10L)).thenReturn(actor);
        when(auditContext.currentIpAddress()).thenReturn("203.0.113.7");

        auditLogService.record(AuditEntityType.TASK, 42L, AuditAction.CREATE, "Task created", group);

        AuditLog saved = captureSaved();
        assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.TASK);
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getActionMessage()).isEqualTo("Task created");
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getActorUser()).isEqualTo(actorUser);
        assertThat(saved.getGroup()).isEqualTo(group);
        assertThat(saved.getGroupMember()).isEqualTo(actor);
    }

    @Test
    void record_nullGroup_leavesGroupAndMemberUnset() {
        User actorUser = User.builder().idUser(1L).build();
        when(auditContext.currentUser()).thenReturn(actorUser);

        auditLogService.record(AuditEntityType.USER, 1L, AuditAction.LOGIN, "User logged in", null);

        AuditLog saved = captureSaved();
        assertThat(saved.getGroup()).isNull();
        assertThat(saved.getGroupMember()).isNull();
        assertThat(saved.getActorUser()).isEqualTo(actorUser);
        assertThat(saved.getAction()).isEqualTo("LOGIN");
    }

    @Test
    void record_nullGroup_doesNotResolveActingMember() {
        auditLogService.record(AuditEntityType.USER, 1L, AuditAction.LOGOUT, "User logged out", null);

        // group-less events must never hit the membership lookup
        verify(auditContext, never()).currentActor(any());
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
