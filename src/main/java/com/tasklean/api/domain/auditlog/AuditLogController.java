package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only REST endpoints for the audit trail — query by group, entity, or member.
 */
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
    @GetMapping("/member/{groupMemberId}")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByMember(@PathVariable Long groupMemberId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByMember(groupMemberId)));
    }
}
