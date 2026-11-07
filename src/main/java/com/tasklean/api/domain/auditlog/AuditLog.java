package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_audit_log")
    private Long idAuditLog;

    @Column(name = "uid", unique = true, nullable = false, length = 100)
    private String uid;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "action_message", nullable = false, columnDefinition = "TEXT")
    private String actionMessage;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // Which fields an update touched, by DTO name. Null on anything that is not a tracked update.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields")
    private List<String> changedFields;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_member_id")
    private GroupMember groupMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;
}