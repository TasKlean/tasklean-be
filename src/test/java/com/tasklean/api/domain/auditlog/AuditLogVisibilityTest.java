package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import com.tasklean.api.security.GroupSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Which query a caller's trail read runs. The narrowing itself is SQL and is verified against the
 * real database; what this pins down is that the choice between the two is made by role, and that
 * a plain member never reaches the unnarrowed finder.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogVisibilityTest {

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private GroupSecurity groupSecurity;

    @InjectMocks
    private AuditLogVisibility auditLogVisibility;

    private AuthPrincipal caller(UserRole role) {
        return new AuthPrincipal(USER_ID, "usr-1", "user@example.com", role);
    }

    @Test
    void inGroup_groupAdmin_readsTheWholeTrail() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);

        auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER));

        verify(auditLogRepository).findByGroupIdGroupOrderByDateCreatedDesc(GROUP_ID);
        verify(auditLogRepository, never()).findInGroupVisibleToMember(anyLong(), anyLong(), anyString());
    }

    @Test
    void inGroup_plainMember_readsOnlyTheirOwnSlice() {
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(false);

        auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER));

        // Keyed on CREATE: the trail is what says who made a thing, since only task stores it.
        verify(auditLogRepository).findInGroupVisibleToMember(GROUP_ID, USER_ID, AuditAction.CREATE.name());
        verify(auditLogRepository, never()).findByGroupIdGroupOrderByDateCreatedDesc(any());
    }

    @Test
    void inGroup_platformAdmin_readsTheWholeTrailWithoutAMembership() {
        // Support access reads across households, so the group role is not even consulted.
        auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.ADMIN));

        verify(auditLogRepository).findByGroupIdGroupOrderByDateCreatedDesc(GROUP_ID);
        verifyNoInteractions(groupSecurity);
    }

    @Test
    void inGroup_superAdmin_readsTheWholeTrail() {
        auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.SUPER_ADMIN));

        verify(auditLogRepository).findByGroupIdGroupOrderByDateCreatedDesc(GROUP_ID);
    }

    @Test
    void inGroup_entriesFound_arePassedThroughUntouched() {
        AuditLog entry = AuditLog.builder().uid("aud-1").build();
        when(groupSecurity.isAdminOfGroup(GROUP_ID)).thenReturn(true);
        when(auditLogRepository.findByGroupIdGroupOrderByDateCreatedDesc(GROUP_ID)).thenReturn(List.of(entry));

        assertThat(auditLogVisibility.inGroup(GROUP_ID, caller(UserRole.USER))).containsExactly(entry);
    }
}
