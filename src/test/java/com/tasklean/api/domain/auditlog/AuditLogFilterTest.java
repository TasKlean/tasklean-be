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

    private AuditLogQueryRequest asked(String entityType, String entityRef) {
        return new AuditLogQueryRequest(entityType, entityRef, null, 20);
    }

    private void apply(Specification<AuditLog> spec) {
        spec.toPredicate(root, query, cb);
    }

    // Resolved before the call, not inside a verify argument: a deep stub reached while Mockito is
    // mid-verification is read as the call being verified.
    private final Path<Object> entityTypePath = root.get("entityType");
    private final Path<Object> entityIdPath = root.get("entityId");

    @Test
    void narrow_nothingAsked_leavesTheScopeAsItIs() {
        assertThat(auditLogFilter.narrow(scope, asked(null, null))).isSameAs(scope);
    }

    @Test
    void narrow_blankParameters_countAsNothingAsked() {
        // An empty query parameter binds to "" rather than null, and an empty filter is no filter.
        assertThat(auditLogFilter.narrow(scope, asked("  ", ""))).isSameAs(scope);
    }

    @Test
    void narrow_entityTypeOnly_keepsThatKindOfEntity() {
        apply(auditLogFilter.narrow(scope, asked(AuditEntityType.TASK, null)));

        verify(cb).equal(entityTypePath, AuditEntityType.TASK);
        verify(cb, never()).equal(entityIdPath, AuditEntityType.TASK);
    }

    @Test
    void narrow_entityTypeAndRef_resolvesTheRefToTheStoredId() {
        // The API addresses a task by uid, so the filter has to come back through AuditEntityRefs
        // before it can match the id the trail actually stores.
        when(auditEntityRefs.resolveId(AuditEntityType.TASK, "tsk-9")).thenReturn(Optional.of(42L));

        apply(auditLogFilter.narrow(scope, asked(AuditEntityType.TASK, "tsk-9")));

        verify(cb).equal(entityTypePath, AuditEntityType.TASK);
        verify(cb).equal(entityIdPath, 42L);
    }

    @Test
    void narrow_refThatResolvesToNothing_narrowsToAnEmptyFeed() {
        // Not a 400: the trail outlives what it describes, so asking after something already gone
        // is fair - there is simply nothing recorded under that reference.
        when(auditEntityRefs.resolveId(AuditEntityType.TASK, "tsk-gone")).thenReturn(Optional.empty());

        apply(auditLogFilter.narrow(scope, asked(AuditEntityType.TASK, "tsk-gone")));

        verify(cb).disjunction();
        verify(cb, never()).equal(entityIdPath, 42L);
    }

    @Test
    void narrow_refWithoutAType_isRefused() {
        // The same reference means different rows per type, so a ref alone cannot be resolved.
        assertThatThrownBy(() -> auditLogFilter.narrow(scope, asked(null, "tsk-9")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void narrow_typeTheTrailNeverRecords_isRefused() {
        // A 400 rather than an empty feed: the set is closed, so this can only be a typo.
        assertThatThrownBy(() -> auditLogFilter.narrow(scope, asked("TASKS", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void narrow_unknownType_isRefusedBeforeAnyLookup() {
        assertThatThrownBy(() -> auditLogFilter.narrow(scope, asked("TASKS", "tsk-9")))
                .isInstanceOf(InvalidRequestException.class);

        verify(auditEntityRefs, never()).resolveId(anyString(), anyString());
    }

    @Test
    void narrow_idAddressedType_passesTheRefStraightThrough() {
        // Category, Tag and TaskCompletion are addressed by their Long id, which AuditEntityRefs
        // parses rather than looks up - the filter does not care which it was.
        when(auditEntityRefs.resolveId(AuditEntityType.CATEGORY, "4")).thenReturn(Optional.of(4L));

        apply(auditLogFilter.narrow(scope, asked(AuditEntityType.CATEGORY, "4")));

        verify(cb).equal(entityIdPath, 4L);
    }
}
