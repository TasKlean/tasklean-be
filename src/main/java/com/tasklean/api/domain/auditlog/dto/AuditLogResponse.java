package com.tasklean.api.domain.auditlog.dto;

import com.tasklean.api.domain.auditlog.AuditFieldChange;
import com.tasklean.api.domain.auditlog.AuditLog;
import com.tasklean.api.domain.user.dto.UserSummaryResponse;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

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
    private List<AuditFieldChange> changedFields;
    private LocalDateTime dateCreated;

    /**
     * Maps one entry, naming the entity and the actor rather than their internal ids, and leaving
     * the IP address out. This is the safe default: an IP belongs to the person who acted, so a
     * reader who is not that person must not be handed it.
     *
     * @param auditLog  the entry
     * @param entityRef the entity's external identifier, resolved for the whole page by
     *                  {@code AuditEntityRefs}; {@code null} if its target is gone
     * @return the response for this entry, with no IP address
     */
    public static AuditLogResponse from(AuditLog auditLog, String entityRef) {
        return base(auditLog, entityRef).build();
    }

    /**
     * Maps one entry including its IP address. Only for an entry whose actor is the reader
     * themselves - see {@code from} for why.
     *
     * @param auditLog  the entry
     * @param entityRef the entity's external identifier; {@code null} if its target is gone
     * @return the response for this entry, carrying the IP address it was recorded with
     */
    public static AuditLogResponse withIpAddress(AuditLog auditLog, String entityRef) {
        return base(auditLog, entityRef).ipAddress(auditLog.getIpAddress()).build();
    }

    private static AuditLogResponseBuilder base(AuditLog auditLog, String entityRef) {
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
                // Null on anything but a tracked update, so absent rather than an empty list. A
                // field's from/to are absent too unless the update disclosed them.
                .changedFields(auditLog.getChangedFields())
                .dateCreated(auditLog.getDateCreated());
    }
}
