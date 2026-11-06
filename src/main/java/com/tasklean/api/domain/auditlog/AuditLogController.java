package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only REST endpoints for the audit trail — query by group, entity, or member.
 */
@Tag(name = "Audit logs", description = "Append-only record of who changed what within a household.")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Lists a group's audit-log entries, newest first.
     *
     * @param groupId the group id
     * @return {@code 200 OK} with the entries
     */
    @PreAuthorize("hasRole('ADMIN') or @groupSecurity.isAdminOfGroup(#groupId)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByGroup(groupId)));
    }

    /**
     * Lists audit-log entries for a specific entity, newest first.
     *
     * @param entityType the entity type (e.g. "task", "group")
     * @param entityId   the entity id
     * @return {@code 200 OK} with the entries
     */
    // Stricter than the group feed on purpose: the type is a caller-supplied string, so there is no
    // group to resolve a role against, and USER rows would expose another person's sign-in history.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/entity")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByEntity(
            @RequestParam String entityType, @RequestParam Long entityId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByEntity(entityType, entityId)));
    }

    /**
     * Lists audit-log entries recorded for a specific group member, newest first.
     *
     * @param groupMemberId the group member id
     * @return {@code 200 OK} with the entries
     */
    // An internal membership id, so @groupSecurity has no uid to resolve the owning group from.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/member/{groupMemberId}")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByMember(@PathVariable Long groupMemberId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByMember(groupMemberId)));
    }
}
