package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final String GROUP_UID = "grp-1";

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditContext auditContext;

    @Mock
    private AuditEntityRefs auditEntityRefs;

    @Mock
    private AuditLogVisibility auditLogVisibility;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    // --- record ---

    @Test
    void recordEvent_withGroup_resolvesAndStampsActingMember() {
        Group group = Group.builder().idGroup(10L).build();
        User actorUser = User.builder().idUser(1L).build();
        GroupMember actor = GroupMember.builder().idGroupMember(5L).build();

        when(auditContext.currentUser()).thenReturn(actorUser);
        when(auditContext.currentActor(10L)).thenReturn(actor);
        when(auditContext.currentIpAddress()).thenReturn("203.0.113.7");

        auditLogService.recordEvent(AuditEntityType.TASK, 42L, AuditAction.CREATE, "Task created", group);

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
    void recordEvent_nullGroup_leavesGroupAndMemberUnset() {
        User actorUser = User.builder().idUser(1L).build();
        when(auditContext.currentUser()).thenReturn(actorUser);

        auditLogService.recordEvent(AuditEntityType.USER, 1L, AuditAction.LOGIN, "User logged in", null);

        AuditLog saved = captureSaved();
        assertThat(saved.getGroup()).isNull();
        assertThat(saved.getGroupMember()).isNull();
        assertThat(saved.getActorUser()).isEqualTo(actorUser);
        assertThat(saved.getAction()).isEqualTo("LOGIN");
    }

    @Test
    void recordEvent_nullGroup_doesNotResolveActingMember() {
        auditLogService.recordEvent(AuditEntityType.USER, 1L, AuditAction.LOGOUT, "User logged out", null);

        // group-less events must never hit the membership lookup
        verify(auditContext, never()).currentActor(any());
    }

    @Test
    void recordAccountEvent_stampsSubjectAsActor() {
        User subject = User.builder().idUser(1L).build();
        when(auditContext.userReference(1L)).thenReturn(subject);
        when(auditContext.currentIpAddress()).thenReturn("203.0.113.7");

        auditLogService.recordAccountEvent(1L, AuditAction.PASSWORD_RESET, "Password reset");

        AuditLog saved = captureSaved();
        assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.USER);
        assertThat(saved.getEntityId()).isEqualTo(1L);
        assertThat(saved.getAction()).isEqualTo("PASSWORD_RESET");
        // Self-service events: actor is the subject, so actor_user_id is never null.
        assertThat(saved.getActorUser()).isEqualTo(subject);
        assertThat(saved.getGroup()).isNull();
        assertThat(saved.getGroupMember()).isNull();
        verify(auditContext, never()).currentActor(any());
    }

    @Test
    void recordEvent_always_generatesAUid() {
        // The API addresses an entry by uid like every other row; an entry without one could not
        // be returned at all, so it is generated on the write path rather than defaulted in SQL.
        auditLogService.recordEvent(AuditEntityType.TASK, 42L, AuditAction.CREATE, "Task created", null);

        assertThat(captureSaved().getUid()).isNotBlank();
    }

    @Test
    void recordAccountEvent_always_generatesAUid() {
        auditLogService.recordAccountEvent(1L, AuditAction.LOGIN, "User logged in");

        assertThat(captureSaved().getUid()).isNotBlank();
    }

    // --- reads ---

    @Test
    void getGroupLogs_entry_carriesTheEntityRefAndActorSummary() {
        AuditLog entry = AuditLog.builder()
                .uid("aud-1")
                .entityType(AuditEntityType.TASK)
                .entityId(42L)
                .action(AuditAction.UPDATE.name())
                .actionMessage("Task \"Dishes\" updated")
                .actorUser(User.builder().idUser(1L).uid("usr-1").name("Alice").lastName("Smith").build())
                .build();
        groupExists();
        when(auditLogVisibility.inGroup(eq(10L), any())).thenReturn(List.of(entry));
        when(auditEntityRefs.resolve(List.of(entry)))
                .thenReturn(Map.of(new AuditEntityRefs.EntityKey(AuditEntityType.TASK, 42L), "tsk-9"));

        AuditLogResponse response = auditLogService.getGroupLogs(GROUP_UID, caller()).getFirst();

        // The response names the entity and the person; neither is an internal id any more.
        assertThat(response.getUid()).isEqualTo("aud-1");
        assertThat(response.getEntity().getType()).isEqualTo(AuditEntityType.TASK);
        assertThat(response.getEntity().getRef()).isEqualTo("tsk-9");
        assertThat(response.getActor().getUid()).isEqualTo("usr-1");
        assertThat(response.getActor().getName()).isEqualTo("Alice");
    }

    @Test
    void getGroupLogs_entryWithoutAnActor_mapsActorAsNull() {
        // Written by a public auth endpoint, where the security context has no caller.
        AuditLog entry = AuditLog.builder()
                .uid("aud-2").entityType(AuditEntityType.USER).entityId(1L)
                .action(AuditAction.LOGIN.name()).actionMessage("User logged in").build();
        groupExists();
        when(auditLogVisibility.inGroup(eq(10L), any())).thenReturn(List.of(entry));
        when(auditEntityRefs.resolve(List.of(entry))).thenReturn(Map.of());

        AuditLogResponse response = auditLogService.getGroupLogs(GROUP_UID, caller()).getFirst();

        assertThat(response.getActor()).isNull();
        // Its target resolved to nothing, which must render as no reference rather than fail.
        assertThat(response.getEntity().getRef()).isNull();
    }

    @Test
    void getGroupLogs_wholePage_resolvesRefsOnce() {
        List<AuditLog> entries = List.of(
                AuditLog.builder().uid("a").entityType(AuditEntityType.TASK).entityId(1L).build(),
                AuditLog.builder().uid("b").entityType(AuditEntityType.TASK).entityId(2L).build(),
                AuditLog.builder().uid("c").entityType(AuditEntityType.GROUP).entityId(3L).build());
        groupExists();
        when(auditLogVisibility.inGroup(eq(10L), any())).thenReturn(entries);
        when(auditEntityRefs.resolve(entries)).thenReturn(Map.of());

        auditLogService.getGroupLogs(GROUP_UID, caller());

        // A resolve per row would be a query per entry; the feed is read far more than written.
        verify(auditEntityRefs).resolve(entries);
    }

    @Test
    void getGroupLogs_narrowing_isLeftToTheVisibilityComponent() {
        // The service must not reach past it to the unnarrowed finder - that is the whole trail.
        groupExists();
        when(auditLogVisibility.inGroup(eq(10L), any())).thenReturn(List.of());

        auditLogService.getGroupLogs(GROUP_UID, caller());

        verify(auditLogRepository, never()).findByGroupIdGroupOrderByDateCreatedDesc(any());
    }

    @Test
    void getGroupLogs_unknownOrDeletedGroup_throwsNotFound() {
        // The filtered finder, so a soft-deleted group is gone here as it is everywhere else.
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditLogService.getGroupLogs(GROUP_UID, caller()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAccountLogs_always_readsTheEntriesAboutThatAccount() {
        // Keyed on the entity, not the actor: an event somebody else caused on this account is
        // still this account's history.
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByDateCreatedDesc(AuditEntityType.USER, 7L))
                .thenReturn(List.of());
        when(auditEntityRefs.resolve(List.of())).thenReturn(Map.of());

        assertThat(auditLogService.getAccountLogs(7L)).isEmpty();

        verify(auditLogRepository).findByEntityTypeAndEntityIdOrderByDateCreatedDesc(AuditEntityType.USER, 7L);
    }

    private AuthPrincipal caller() {
        return new AuthPrincipal(1L, "usr-1", "user@example.com", UserRole.USER);
    }

    private void groupExists() {
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID))
                .thenReturn(Optional.of(Group.builder().idGroup(10L).uid(GROUP_UID).build()));
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
