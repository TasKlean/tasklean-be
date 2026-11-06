package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.dto.AuditLogPage;
import com.tasklean.api.domain.auditlog.dto.AuditLogQueryRequest;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final String GROUP_UID = "grp-1";
    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC);

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditContext auditContext;

    @Mock
    private AuditEntityRefs auditEntityRefs;

    @Mock
    private AuditLogVisibility auditLogVisibility;

    @Mock
    private AuditLogFilter auditLogFilter;

    @Mock
    private AuditLogCursor auditLogCursor;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @BeforeEach
    void collaboratorsPassTheirScopeThrough() {
        // Lenient because the write tests touch neither; a read test that cares about the
        // composition stubs these again, and the later stub is the one that answers.
        lenient().when(auditLogVisibility.inGroup(any(), any())).thenReturn(Specification.unrestricted());
        lenient().when(auditLogFilter.narrow(any(), any())).thenAnswer(call -> call.getArgument(0));
    }

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
                .actionMessage("Task updated")
                .actorUser(User.builder().idUser(1L).uid("usr-1").name("Alice").lastName("Smith").build())
                .build();
        groupExists();
        whenFeedHolds(List.of(entry));
        when(auditEntityRefs.resolve(List.of(entry)))
                .thenReturn(Map.of(new AuditEntityRefs.EntityKey(AuditEntityType.TASK, 42L), "tsk-9"));

        AuditLogResponse response = auditLogService.getGroupLogs(GROUP_UID, caller(), query(20))
                .getItems().getFirst();

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
        whenFeedHolds(List.of(entry));

        AuditLogResponse response = auditLogService.getGroupLogs(GROUP_UID, caller(), query(20))
                .getItems().getFirst();

        assertThat(response.getActor()).isNull();
        // Its target resolved to nothing, which must render as no reference rather than fail.
        assertThat(response.getEntity().getRef()).isNull();
    }

    @Test
    void getGroupLogs_wholePage_resolvesRefsOnce() {
        List<AuditLog> entries = List.of(entryWithKey(3L), entryWithKey(2L), entryWithKey(1L));
        groupExists();
        whenFeedHolds(entries);

        auditLogService.getGroupLogs(GROUP_UID, caller(), query(20));

        // A resolve per row would be a query per entry; the feed is read far more than written.
        verify(auditEntityRefs).resolve(entries);
    }

    @Test
    void getGroupLogs_always_readsTheFeedTheVisibilityRuleAndTheFilterAgreedOn() {
        // The service composes and must add no predicate of its own: one built here would sit
        // outside both the member rule and the caller's filter.
        groupExists();
        Specification<AuditLog> visible = Specification.unrestricted();
        Specification<AuditLog> narrowed = Specification.unrestricted();
        when(auditLogVisibility.inGroup(eq(10L), any())).thenReturn(visible);
        when(auditLogFilter.narrow(eq(visible), any())).thenReturn(narrowed);
        whenFeedHolds(List.of());

        auditLogService.getGroupLogs(GROUP_UID, caller(), query(20));

        verify(auditLogRepository).count(narrowed);
    }

    @Test
    void getGroupLogs_unknownOrDeletedGroup_throwsNotFound() {
        // The filtered finder, so a soft-deleted group is gone here as it is everywhere else.
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditLogService.getGroupLogs(GROUP_UID, caller(), query(20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAccountLogs_always_narrowsItsOwnScopeByTheSameFilter() {
        // Keyed on the entity, not the actor: an event somebody else caused on this account is
        // still this account's history.
        whenFeedHolds(List.of());

        assertThat(auditLogService.getAccountLogs(7L, query(20)).getItems()).isEmpty();

        verify(auditLogFilter).narrow(any(), any());
    }

    // --- ip address visibility ---

    @Test
    void getGroupLogs_evenTheCallersOwnEntry_omitsTheIpAddress() {
        // Never on a group trail, not even for the person it belongs to: the feed is read by every
        // member, so an address on it would be one member's address shown to the household.
        groupExists();
        whenFeedHolds(List.of(entryActedBy(1L)));

        AuditLogResponse response = auditLogService.getGroupLogs(GROUP_UID, caller(), query(20))
                .getItems().getFirst();

        assertThat(response.getIpAddress()).isNull();
    }

    @Test
    void getAccountLogs_theCallersOwnAction_carriesTheIpAddress() {
        // Their own address back to them is what makes a strange sign-in recognisable.
        whenFeedHolds(List.of(entryActedBy(7L)));

        AuditLogResponse response = auditLogService.getAccountLogs(7L, query(20)).getItems().getFirst();

        assertThat(response.getIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void getAccountLogs_anActionSomebodyElseTook_omitsTheIpAddress() {
        // A super-admin changing this account's role writes an entry here carrying the admin's
        // address, which belongs to the admin and not to the account reading it.
        whenFeedHolds(List.of(entryActedBy(2L)));

        AuditLogResponse response = auditLogService.getAccountLogs(7L, query(20)).getItems().getFirst();

        assertThat(response.getIpAddress()).isNull();
        assertThat(response.getActor().getUid()).isEqualTo("usr-2");
    }

    @Test
    void getAccountLogs_anEntryWithNoActor_omitsTheIpAddress() {
        // Written by a public auth endpoint, so there is nobody to match the reader against.
        whenFeedHolds(List.of(AuditLog.builder().uid("aud-9").entityType(AuditEntityType.USER)
                .entityId(7L).action(AuditAction.LOGIN.name()).ipAddress("203.0.113.7").build()));

        AuditLogResponse response = auditLogService.getAccountLogs(7L, query(20)).getItems().getFirst();

        assertThat(response.getIpAddress()).isNull();
    }

    // --- paging ---

    @Test
    void getGroupLogs_moreEntriesThanThePageHolds_handsOutANextCursor() {
        // The read asks for one row beyond the page, so the extra one is what proves there is more.
        groupExists();
        whenFeedHolds(List.of(entryWithKey(3L), entryWithKey(2L), entryWithKey(1L)));
        when(auditLogCursor.encode(any())).thenReturn("next-cursor");

        AuditLogPage page = auditLogService.getGroupLogs(GROUP_UID, caller(), query(2));

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getNextCursor()).isEqualTo("next-cursor");
    }

    @Test
    void getGroupLogs_lastPage_handsOutNoCursor() {
        groupExists();
        whenFeedHolds(List.of(entryWithKey(2L), entryWithKey(1L)));

        AuditLogPage page = auditLogService.getGroupLogs(GROUP_UID, caller(), query(5));

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void getGroupLogs_pageExactlyFull_handsOutNoCursor() {
        // The boundary a "was the page full" check gets wrong: 2 of 2 asked for, nothing beyond.
        groupExists();
        whenFeedHolds(List.of(entryWithKey(2L), entryWithKey(1L)));

        assertThat(auditLogService.getGroupLogs(GROUP_UID, caller(), query(2)).getNextCursor()).isNull();
    }

    @Test
    void getGroupLogs_always_asksForOneRowBeyondThePageNewestFirst() {
        groupExists();
        whenFeedHolds(List.of());

        auditLogService.getGroupLogs(GROUP_UID, caller(), query(20));

        RecordedQuery recorded = appliedFluentQuery();
        assertThat(recorded.limit).isEqualTo(21);
        // Both keys in this order: the id alone cannot order a feed, and the timestamp alone
        // cannot break a tie inside it.
        assertThat(recorded.sort).isEqualTo(
                Sort.by(Sort.Direction.DESC, "dateCreated").and(Sort.by(Sort.Direction.DESC, "idAuditLog")));
    }

    @Test
    void getGroupLogs_noLimitAsked_readsTheDefaultPage() {
        // "limit=" with nothing after it binds to null, which must not reach the query as a zero.
        groupExists();
        whenFeedHolds(List.of());

        auditLogService.getGroupLogs(GROUP_UID, caller(), new AuditLogQueryRequest(null, null, null, null));

        assertThat(appliedFluentQuery().limit).isEqualTo(AuditLogQueryRequest.DEFAULT_LIMIT + 1);
    }

    @Test
    void getGroupLogs_always_reportsTheTotalBesideThePage() {
        groupExists();
        whenFeedHolds(List.of(entryWithKey(1L)));
        when(auditLogRepository.count(any(Specification.class))).thenReturn(57L);

        assertThat(auditLogService.getGroupLogs(GROUP_UID, caller(), query(20)).getTotal()).isEqualTo(57L);
    }

    @Test
    void getGroupLogs_cursorOnTheRequest_isNotCountedIntoTheTotal() {
        // The total is the size of the feed, not of what is left after the cursor - a total that
        // shrank as the reader paged would be worse than none at all.
        groupExists();
        Specification<AuditLog> feed = Specification.unrestricted();
        when(auditLogFilter.narrow(any(), any())).thenReturn(feed);
        when(auditLogCursor.decode("page-2")).thenReturn(new AuditLogCursor.Position(NOW, 9L));
        whenFeedHolds(List.of());

        AuditLogQueryRequest request = query(20);
        request.setCursor("page-2");
        auditLogService.getGroupLogs(GROUP_UID, caller(), request);

        verify(auditLogRepository).count(feed);
    }

    @Test
    void getGroupLogs_entriesBeyondThePage_areNotResolvedOrReturned() {
        // The trimmed row must not reach the ref lookup either - it is a probe, not a result.
        groupExists();
        List<AuditLog> read = List.of(entryWithKey(2L), entryWithKey(1L));
        whenFeedHolds(read);

        auditLogService.getGroupLogs(GROUP_UID, caller(), query(1));

        verify(auditEntityRefs).resolve(read.subList(0, 1));
    }

    private AuditLogQueryRequest query(int limit) {
        return new AuditLogQueryRequest(null, null, null, limit);
    }

    private AuditLog entryActedBy(Long actorId) {
        return AuditLog.builder().uid("aud-" + actorId).entityType(AuditEntityType.USER).entityId(7L)
                .action(AuditAction.UPDATE.name()).ipAddress("203.0.113.7")
                .actorUser(User.builder().idUser(actorId).uid("usr-" + actorId).name("Alice").build())
                .build();
    }

    private AuditLog entryWithKey(Long id) {
        return AuditLog.builder().uid("aud-" + id).idAuditLog(id).dateCreated(NOW)
                .entityType(AuditEntityType.TASK).entityId(id).build();
    }

    // The repository is a mock, so the function the service hands it never runs; returning the rows
    // directly leaves the page assembly under test. appliedFluentQuery checks the function itself.
    @SuppressWarnings("unchecked")
    private void whenFeedHolds(List<AuditLog> entries) {
        when(auditLogRepository.findBy(any(Specification.class), any(Function.class))).thenReturn(entries);
    }

    @SuppressWarnings("unchecked")
    private RecordedQuery appliedFluentQuery() {
        ArgumentCaptor<Function<Object, Object>> captor = ArgumentCaptor.forClass(Function.class);
        verify(auditLogRepository).findBy(any(Specification.class), captor.capture());

        RecordedQuery recorded = new RecordedQuery();
        JpaSpecificationExecutor.SpecificationFluentQuery<AuditLog> fluent =
                mock(JpaSpecificationExecutor.SpecificationFluentQuery.class);
        when(fluent.sortBy(any())).thenAnswer(call -> {
            recorded.sort = call.getArgument(0);
            return fluent;
        });
        when(fluent.limit(anyInt())).thenAnswer(call -> {
            recorded.limit = call.getArgument(0);
            return fluent;
        });
        captor.getValue().apply(fluent);
        return recorded;
    }

    private static final class RecordedQuery {
        private Sort sort;
        private int limit;
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
