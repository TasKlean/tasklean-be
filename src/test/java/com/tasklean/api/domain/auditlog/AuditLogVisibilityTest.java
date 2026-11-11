package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Which predicate a caller's trail read carries. The specification is a lambda, so each case is
 * settled by applying it to a recording criteria builder: the member rule shows up as a correlated
 * subquery, and an admin's read must not carry one at all.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogVisibilityTest {

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private AuditLogVisibility auditLogVisibility;

    @SuppressWarnings("unchecked")
    private final Root<AuditLog> root = mock(Root.class, RETURNS_DEEP_STUBS);

    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);

    private final CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

    private AuthPrincipal caller(UserRole role) {
        return new AuthPrincipal(USER_ID, "usr-1", "user@example.com", role);
    }

    private void applyAs(UserRole role) {
        auditLogVisibility.inGroup(GROUP_ID, caller(role)).scope().toPredicate(root, query, cb);
    }

    @Test
    void inGroup_groupAdmin_readsTheWholeTrail() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        applyAs(UserRole.USER);

        verify(cb).equal(root.get("group").get("idGroup"), GROUP_ID);
        // No subquery means no member rule: an admin reads every row in the group.
        verify(query, never()).subquery(any(Class.class));
    }

    @Test
    void inGroup_plainMember_readsOnlyTheirOwnSlice() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        applyAs(UserRole.USER);

        // Keyed on CREATE: the trail is what says who made a thing, since only task stores it.
        verify(query).subquery(Long.class);
        verify(cb).exists(any());
    }

    @Test
    void inGroup_platformAdmin_readsTheWholeTrailWithoutAMembership() {
        // Support access reads across households, so the group role is not even consulted.
        applyAs(UserRole.ADMIN);

        verify(query, never()).subquery(any(Class.class));
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void inGroup_superAdmin_readsTheWholeTrail() {
        applyAs(UserRole.SUPER_ADMIN);

        verify(query, never()).subquery(any(Class.class));
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void inGroup_groupAdmin_mayFilterByAnyActor() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        AuditFilterRights rights = auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER)).rights();

        assertThat(rights.mayFilterByActor("usr-someone-else")).isTrue();
    }

    @Test
    void inGroup_plainMember_mayFilterOnlyByThemselves() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        AuditFilterRights rights = auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER)).rights();

        assertThat(rights.mayFilterByActor("usr-1")).isTrue();
        assertThat(rights.mayFilterByActor("usr-someone-else")).isFalse();
    }

    @Test
    void inGroup_always_decidesRowsAndRightsFromOneGroupLookup() {
        // Both answers ride on the same membership, so asking for them must not cost two queries.
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER));

        verify(groupSecurity).isAdminOfGroup(GROUP_ID);
    }

    @Test
    void inGroup_plainMember_staysScopedToTheGroupAsWellAsTheMemberRule() {
        // The member rule is an extra narrowing, never a replacement: without the group predicate
        // a member would read their own actions in every household at once.
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        applyAs(UserRole.USER);

        verify(cb).equal(root.get("group").get("idGroup"), GROUP_ID);
        verify(cb).and(any(), any());
    }
}
