package com.tasklean.api.domain.auditlog.dto;

import com.tasklean.api.domain.auditlog.AuditLog;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long id;
    private String entityType;
    private Long entityId;
    private String action;
    private String actionMessage;
    private String ipAddress;
    private LocalDateTime dateCreated;
    private Long groupMemberId;
    private Long groupId;

    public static AuditLogResponse from(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getIdAuditLog())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .action(auditLog.getAction())
                .actionMessage(auditLog.getActionMessage())
                .ipAddress(auditLog.getIpAddress())
                .dateCreated(auditLog.getDateCreated())
                .groupMemberId(auditLog.getGroupMember() != null ? auditLog.getGroupMember().getIdGroupMember() : null)
                .groupId(auditLog.getGroup() != null ? auditLog.getGroup().getIdGroup() : null)
                .build();
    }
}
