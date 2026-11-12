package com.tasklean.api.domain.auditlog;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The predicates a feed is composed from. Built against a recording criteria builder rather than a
 * database: what matters here is the shape each specification asks for, which is also the only part
 * no live request can show you in isolation.
 */
class AuditLogSpecsTest {

    @SuppressWarnings("unchecked")
    private final Root<AuditLog> root = mock(Root.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    @Test
    void visibleToMember_always_asksWhetherTheCallerCreatedTheEntity() {
        // The correlated subquery is the rule: without it a member sees only their own actions,
        // and the history of a task they made would be invisible to them the moment anyone edited it.
        AuditLogSpecs.visibleToMember(10L, 1L).toPredicate(root, query, cb);

        verify(query).subquery(Long.class);
        verify(cb).exists(any());
        verify(cb).or(any(), any());
    }

    @Test
    void inGroup_always_narrowsToThatGroupAlone() {
        AuditLogSpecs.inGroup(10L).toPredicate(root, query, cb);

        verify(cb).equal(root.get("group").get("idGroup"), 10L);
        // No subquery: an admin's read must not pay for the member rule it does not use.
        verify(query, never()).subquery(any(Class.class));
    }

    @Test
    void after_always_tiesTheRowIdToTheTimestamp() {
        LocalDateTime when = LocalDateTime.now(ZoneOffset.UTC);

        AuditLogSpecs.after(when, 9L).toPredicate(root, query, cb);

        // Strictly older, or the same instant with a smaller id - an "older than" alone would
        // drop or repeat entries written within the same instant.
        verify(cb).lessThan(root.get("dateCreated"), when);
        verify(cb).lessThan(root.get("idAuditLog"), 9L);
        verify(cb).or(any(), any());
    }

    @Test
    void byActor_always_matchesOnTheActingUser() {
        AuditLogSpecs.byActor(9L).toPredicate(root, query, cb);

        verify(cb).equal(root.get("actorUser").get("idUser"), 9L);
    }

    @Test
    void recordedFrom_always_includesTheBoundItself() {
        LocalDateTime from = LocalDateTime.now(ZoneOffset.UTC);

        AuditLogSpecs.recordedFrom(from).toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(root.<LocalDateTime>get("dateCreated"), from);
    }

    @Test
    void recordedBefore_always_stopsShortOfTheBound() {
        // Exclusive, so a bound never has to name a fraction of a second to cover a whole one.
        LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC);

        AuditLogSpecs.recordedBefore(to).toPredicate(root, query, cb);

        verify(cb).lessThan(root.<LocalDateTime>get("dateCreated"), to);
    }

    @Test
    void withActorFetched_aRowQuery_fetchesTheActorWithTheRow() {
        // The response renders the actor's name, and no JPA session outlives the service call.
        when(query.getResultType()).thenReturn(Object.class);

        assertThat(AuditLogSpecs.withActorFetched().toPredicate(root, query, cb)).isNull();

        verify(root).fetch("actorUser", JoinType.LEFT);
    }

    @Test
    void withActorFetched_aCountQuery_addsNoFetch() {
        // Hibernate rejects a fetch whose owner is not in the selection, which a count never is.
        CriteriaQuery<Long> counting = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
        when(counting.getResultType()).thenReturn(Long.class);

        AuditLogSpecs.withActorFetched().toPredicate(root, counting, cb);

        verify(root, never()).fetch(any(String.class), any(JoinType.class));
    }

    @Test
    void aboutEntity_always_matchesOneEntityOfOneType() {
        AuditLogSpecs.aboutEntity(AuditEntityType.TASK, 42L).toPredicate(root, query, cb);

        verify(cb).equal(root.get("entityType"), AuditEntityType.TASK);
        verify(cb).equal(root.get("entityId"), 42L);
    }

    @Test
    void visibleToMember_subquery_isScopedToTheSameGroup() {
        // Without the group predicate a CREATE the caller made in another household would unlock
        // entries here, since entity ids are only unique per table.
        Subquery<Long> created = mock(Subquery.class, RETURNS_DEEP_STUBS);
        when(query.subquery(Long.class)).thenReturn(created);

        AuditLogSpecs.visibleToMember(10L, 1L).toPredicate(root, query, cb);

        verify(cb).equal(created.from(AuditLog.class).get("group").get("idGroup"), 10L);
        verify(cb).equal(created.from(AuditLog.class).get("action"), AuditAction.CREATE.name());
    }
}
