package com.tasklean.api.domain.auditlog;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only REST endpoints for the audit trail, in its two scopes: one group's history, and the
 * caller's own account history.
 */
@Tag(name = "Audit logs", description = "Append-only record of who changed what within a household.")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Lists what the caller may see of a group's audit trail, newest first. Any member of the
     * group may read it; an admin sees all of it, a plain member their own actions plus the
     * history of what they created.
     *
     * @param groupUid  the group's public UID
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the visible entries
     */
    // Membership is the gate; which rows come back is decided per row in the query, since a
    // member and an admin of the same group are both allowed here but see different trails.
    @PreAuthorize("hasRole('ADMIN') or @groupSecurity.isMember(#groupUid)")
    @GetMapping("/groups/{groupUid}")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getGroupLogs(
            @PathVariable String groupUid, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getGroupLogs(groupUid, principal)));
    }

    /**
     * Lists the caller's own account history, newest first - sign-ins, credential changes,
     * two-factor and device events.
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the caller's account entries
     */
    // Self-scoped, so there is no identifier in the path and nothing to authorize beyond being
    // signed in - the caller cannot name anybody else's account here.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/account")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAccountLogs(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getAccountLogs(principal.userId())));
    }
}
