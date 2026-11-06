package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Which query a caller's trail read runs, and how a cursor reaches it. The narrowing itself is SQL
 * and is verified against the real database; what this pins down is that the choice between the two
 * queries is made by role, and that a plain member never reaches the unnarrowed one.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogVisibilityTest {

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final int LIMIT = 20;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private AuditLogVisibility auditLogVisibility;

    private AuthPrincipal caller(UserRole role) {
        return new AuthPrincipal(USER_ID, "usr-1", "user@example.com", role);
    }

    private List<AuditLog> readAs(UserRole role) {
        return auditLogVisibility.inGroup(GROUP_ID, caller(role), null, LIMIT);
    }

    // --- which query the page comes from ---

    @Test
    void inGroup_groupAdmin_readsTheWholeTrail() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        readAs(UserRole.USER);

        verify(auditLogRepository).findInGroup(eq(GROUP_ID), any(), any(), any());
        verify(auditLogRepository, never())
                .findInGroupVisibleToMember(anyLong(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void inGroup_plainMember_readsOnlyTheirOwnSlice() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        readAs(UserRole.USER);

        // Keyed on CREATE: the trail is what says who made a thing, since only task stores it.
        verify(auditLogRepository).findInGroupVisibleToMember(
                eq(GROUP_ID), eq(USER_ID), eq(AuditAction.CREATE.name()), any(), any(), any());
        verify(auditLogRepository, never()).findInGroup(any(), any(), any(), any());
    }

    @Test
    void inGroup_platformAdmin_readsTheWholeTrailWithoutAMembership() {
        // Support access reads across households, so the group role is not even consulted.
        readAs(UserRole.ADMIN);

        verify(auditLogRepository).findInGroup(eq(GROUP_ID), any(), any(), any());
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void inGroup_superAdmin_readsTheWholeTrail() {
        readAs(UserRole.SUPER_ADMIN);

        verify(auditLogRepository).findInGroup(eq(GROUP_ID), any(), any(), any());
    }

    @Test
    void inGroup_entriesFound_arePassedThroughUntouched() {
        AuditLog entry = AuditLog.builder().uid("aud-1").build();
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);
        when(auditLogRepository.findInGroup(eq(GROUP_ID), any(), any(), any())).thenReturn(List.of(entry));

        assertThat(readAs(UserRole.USER)).containsExactly(entry);
    }

    // --- how the cursor reaches the query ---

    @Test
    void inGroup_cursorPosition_isPassedToTheQueryAsBothItsKeys() {
        // A timestamp alone is not a position: entries written in the same instant would repeat
        // across pages or be skipped, so the row id rides along as the tiebreak.
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);
        LocalDateTime when = LocalDateTime.now(ZoneOffset.UTC);

        auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER), new AuditLogCursor.Position(when, 9L), LIMIT);

        verify(auditLogRepository).findInGroup(GROUP_ID, when, 9L, Limit.of(LIMIT));
    }

    @Test
    void inGroup_firstPage_asksWithNoPosition() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        readAs(UserRole.USER);

        verify(auditLogRepository).findInGroup(GROUP_ID, null, null, Limit.of(LIMIT));
    }

    // --- the total beside the page runs through the same branch ---

    @Test
    void countInGroup_groupAdmin_countsTheWholeTrail() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        auditLogVisibility.countInGroup(GROUP_ID, caller(UserRole.USER));

        verify(auditLogRepository).countInGroup(GROUP_ID);
        verify(auditLogRepository, never()).countInGroupVisibleToMember(anyLong(), anyLong(), anyString());
    }

    @Test
    void countInGroup_plainMember_countsOnlyTheirOwnSlice() {
        // A total counting rows the member cannot read would promise pages that never arrive.
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        auditLogVisibility.countInGroup(GROUP_ID, caller(UserRole.USER));

        verify(auditLogRepository).countInGroupVisibleToMember(GROUP_ID, USER_ID, AuditAction.CREATE.name());
        verify(auditLogRepository, never()).countInGroup(any());
    }

    @Test
    void countInGroup_platformAdmin_countsTheWholeTrailWithoutAMembership() {
        auditLogVisibility.countInGroup(GROUP_ID, caller(UserRole.ADMIN));

        verify(auditLogRepository).countInGroup(GROUP_ID);
        verifyNoInteractions(groupSecurity);
    }
}
