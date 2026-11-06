package com.tasklean.api.domain.auditlog.dto;

import com.tasklean.api.domain.auditlog.AuditLog;
import com.tasklean.api.domain.user.dto.UserSummaryResponse;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private String uid;
    private String action;
    private String actionMessage;
    private AuditEntityResponse entity;
    private UserSummaryResponse actor;
    private String ipAddress;
    private LocalDateTime dateCreated;

    /**
     * Maps one entry, naming the entity and the actor rather than their internal ids.
     *
     * @param auditLog  the entry
     * @param entityRef the entity's external identifier, resolved for the whole page by
     *                  {@code AuditEntityRefs}; {@code null} if its target is gone
     * @return the response for this entry
     */
    public static AuditLogResponse from(AuditLog auditLog, String entityRef) {
        return AuditLogResponse.builder()
                .uid(auditLog.getUid())
                .action(auditLog.getAction())
                .actionMessage(auditLog.getActionMessage())
                .entity(AuditEntityResponse.builder()
                        .type(auditLog.getEntityType())
                        .ref(entityRef)
                        .build())
                // Absent on entries written by the public auth endpoints, which have no caller in
                // the security context - there the subject is the entity, not the actor.
                .actor(auditLog.getActorUser() != null
                        ? UserSummaryResponse.from(auditLog.getActorUser()) : null)
                .ipAddress(auditLog.getIpAddress())
                .dateCreated(auditLog.getDateCreated())
                .build();
    }
}
