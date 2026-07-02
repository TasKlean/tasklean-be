package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.auditlog.dto.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByGroup(groupId)));
    }

    @GetMapping("/entity")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByEntity(
            @RequestParam String entityType, @RequestParam Long entityId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByEntity(entityType, entityId)));
    }

    @GetMapping("/member/{groupMemberId}")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByMember(@PathVariable Long groupMemberId) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getLogsByMember(groupMemberId)));
    }
}
