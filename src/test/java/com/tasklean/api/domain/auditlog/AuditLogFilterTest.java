package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.domain.auditlog.dto.AuditLogQueryRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a caller may ask of a feed, and what each answer narrows to. The scope handed in stands for
 * whichever feed is being read, so these cases hold the same for a group's trail and an account's.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogFilterTest {

    @Mock
    private AuditEntityRefs auditEntityRefs;

    @InjectMocks
    private AuditLogFilter auditLogFilter;

    @SuppressWarnings("unchecked")
    private final Root<AuditLog> root = mock(Root.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    private final Specification<AuditLog> scope = Specification.unrestricted();

    private static final String CALLER_UID = "usr-caller";

    // Most cases are about what a filter narrows to rather than who may ask for it, so they run
    // unrestricted; the actor cases below name the rights they are testing.
    private Specification<AuditLog> narrow(AuditLogQueryRequest query) {
        return auditLogFilter.narrow(scope, query, AuditFilterRights.anyActor());
    }

    private Specification<AuditLog> narrowAsMember(AuditLogQueryRequest query) {
        return auditLogFilter.narrow(scope, query, AuditFilterRights.ownActionsOnly(CALLER_UID));
    }

    private AuditLogQueryRequest asked(String entityType, String entityRef) {
        return new AuditLogQueryRequest(entityType, entityRef, null, null, null, null, 20);
    }

    private AuditLogQueryRequest askedOfActor(String actorRef) {
        return new AuditLogQueryRequest(null, null, actorRef, null, null, null, 20);
    }

    private AuditLogQueryRequest askedBetween(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
        return new AuditLogQueryRequest(null, null, null, dateFrom, dateTo, null, 20);
    }

    private static OffsetDateTime utc(int hour) {
        return OffsetDateTime.of(2026, 11, 1, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    private void apply(Specification<AuditLog> spec) {
        spec.toPredicate(root, query, cb);
    }

    // Resolved before the call, not inside a verify argument: a deep stub reached while Mockito is
    // mid-verification is read as the call being verified.
    private final Path<Object> entityTypePath = root.get("entityType");
    private final Path<Object> entityIdPath = root.get("entityId");
    // Typed, unlike the two above: a range comparison needs a Comparable bound, which Path<Object>
    // cannot satisfy.
    private final Path<LocalDateTime> dateCreatedPath = root.get("dateCreated");
    private final Path<Object> actorUserIdPath = root.get("actorUser").get("idUser");

    @Test
    void narrow_nothingAsked_leavesTheScopeAsItIs() {
        assertThat(narrow(asked(null, null))).isSameAs(scope);
    }

    @Test
    void narrow_blankParameters_countAsNothingAsked() {
        // An empty query parameter binds to "" rather than null, and an empty filter is no filter.
        assertThat(narrow(asked("  ", ""))).isSameAs(scope);
    }

    @Test
    void narrow_entityTypeOnly_keepsThatKindOfEntity() {
        apply(narrow(asked(AuditEntityType.TASK, null)));

        verify(cb).equal(entityTypePath, AuditEntityType.TASK);
        verify(cb, never()).equal(entityIdPath, AuditEntityType.TASK);
    }

    @Test
    void narrow_entityTypeAndRef_resolvesTheRefToTheStoredId() {
        // The API addresses a task by uid, so the filter has to come back through AuditEntityRefs
        // before it can match the id the trail actually stores.
        when(auditEntityRefs.resolveId(AuditEntityType.TASK, "tsk-9")).thenReturn(Optional.of(42L));

        apply(narrow(asked(AuditEntityType.TASK, "tsk-9")));

        verify(cb).equal(entityTypePath, AuditEntityType.TASK);
        verify(cb).equal(entityIdPath, 42L);
    }

    @Test
    void narrow_refThatResolvesToNothing_narrowsToAnEmptyFeed() {
        // Not a 400: the trail outlives what it describes, so asking after something already gone
        // is fair - there is simply nothing recorded under that reference.
        when(auditEntityRefs.resolveId(AuditEntityType.TASK, "tsk-gone")).thenReturn(Optional.empty());

        apply(narrow(asked(AuditEntityType.TASK, "tsk-gone")));

        verify(cb).disjunction();
        verify(cb, never()).equal(entityIdPath, 42L);
    }

    @Test
    void narrow_refWithoutAType_isRefused() {
        // The same reference means different rows per type, so a ref alone cannot be resolved.
        assertThatThrownBy(() -> narrow(asked(null, "tsk-9")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void narrow_typeTheTrailNeverRecords_isRefused() {
        // A 400 rather than an empty feed: the set is closed, so this can only be a typo.
        assertThatThrownBy(() -> narrow(asked("TASKS", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void narrow_unknownType_isRefusedBeforeAnyLookup() {
        assertThatThrownBy(() -> narrow(asked("TASKS", "tsk-9")))
                .isInstanceOf(InvalidRequestException.class);

        verify(auditEntityRefs, never()).resolveId(anyString(), anyString());
    }

    @Test
    void narrow_idAddressedType_passesTheRefStraightThrough() {
        // Category, Tag and TaskCompletion are addressed by their Long id, which AuditEntityRefs
        // parses rather than looks up - the filter does not care which it was.
        when(auditEntityRefs.resolveId(AuditEntityType.CATEGORY, "4")).thenReturn(Optional.of(4L));

        apply(narrow(asked(AuditEntityType.CATEGORY, "4")));

        verify(cb).equal(entityIdPath, 4L);
    }

    @Test
    void narrow_dateFromOnly_keepsEntriesFromThatInstantOn() {
        apply(narrow(askedBetween(utc(9), null)));

        verify(cb).greaterThanOrEqualTo(dateCreatedPath, utc(9).toLocalDateTime());
        verify(cb, never()).lessThan(dateCreatedPath, utc(9).toLocalDateTime());
    }

    @Test
    void narrow_dateToOnly_keepsEntriesBeforeThatInstant() {
        apply(narrow(askedBetween(null, utc(17))));

        verify(cb).lessThan(dateCreatedPath, utc(17).toLocalDateTime());
        verify(cb, never()).greaterThanOrEqualTo(dateCreatedPath, utc(17).toLocalDateTime());
    }

    @Test
    void narrow_bothBounds_appliesThemAsAHalfOpenRange() {
        // Inclusive at the start, exclusive at the end: two adjacent ranges must not both claim
        // the entry that sits exactly on the boundary between them.
        apply(narrow(askedBetween(utc(9), utc(17))));

        verify(cb).greaterThanOrEqualTo(dateCreatedPath, utc(9).toLocalDateTime());
        verify(cb).lessThan(dateCreatedPath, utc(17).toLocalDateTime());
    }

    @Test
    void narrow_aBoundAwayFromUtc_isConvertedBeforeComparing() {
        // The whole reason the parameter carries an offset: 09:00+02:00 is 07:00 in the column, and
        // comparing the wall-clock reading instead would silently shift the range by two hours.
        OffsetDateTime nineInSummerTime = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.ofHours(2));

        apply(narrow(askedBetween(nineInSummerTime, null)));

        verify(cb).greaterThanOrEqualTo(dateCreatedPath, utc(7).toLocalDateTime());
    }

    @Test
    void narrow_dateFromAfterDateTo_isRefused() {
        // An empty feed would read as "nothing happened then" rather than as a range the wrong way
        // round, so this is a 400 even though the predicate it would build is harmless.
        assertThatThrownBy(() -> narrow(askedBetween(utc(17), utc(9))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void narrow_anActorRef_resolvesTheUidToTheActingUserId() {
        when(auditEntityRefs.resolveId(AuditEntityType.USER, "usr-9")).thenReturn(Optional.of(9L));

        apply(narrow(askedOfActor("usr-9")));

        verify(cb).equal(actorUserIdPath, 9L);
    }

    @Test
    void narrow_anActorRefThatResolvesToNothing_narrowsToAnEmptyFeed() {
        when(auditEntityRefs.resolveId(AuditEntityType.USER, "usr-gone")).thenReturn(Optional.empty());

        apply(narrow(askedOfActor("usr-gone")));

        verify(cb).disjunction();
    }

    @Test
    void narrow_aMemberNamingThemselves_isAllowed() {
        when(auditEntityRefs.resolveId(AuditEntityType.USER, CALLER_UID)).thenReturn(Optional.of(1L));

        apply(narrowAsMember(askedOfActor(CALLER_UID)));

        verify(cb).equal(actorUserIdPath, 1L);
    }

    @Test
    void narrow_aMemberNamingSomebodyElse_isRefused() {
        assertThatThrownBy(() -> narrowAsMember(askedOfActor("usr-9")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void narrow_aMemberNamingAnActor_isRefusedBeforeAnyLookup() {
        // Refused on the reference rather than on the resolved id: a 403 for a stranger and an
        // empty feed for a uid that does not exist would tell a member which uids are real.
        assertThatThrownBy(() -> narrowAsMember(askedOfActor("usr-nobody")))
                .isInstanceOf(AccessDeniedException.class);

        verify(auditEntityRefs, never()).resolveId(anyString(), anyString());
    }

    @Test
    void narrow_boundsThatMeet_areAllowed() {
        // A zero-width range is a legal question with an empty answer, not a malformed request.
        apply(narrow(askedBetween(utc(9), utc(9))));

        verify(cb).greaterThanOrEqualTo(dateCreatedPath, utc(9).toLocalDateTime());
        verify(cb).lessThan(dateCreatedPath, utc(9).toLocalDateTime());
    }
}
