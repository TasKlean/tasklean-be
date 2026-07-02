package com.tasklean.api.domain.task;

import com.tasklean.api.common.BaseEntity;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.category.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_task")
    private Long idTask;

    @Column(name = "uid", unique = true, nullable = false, length = 500)
    private String uid;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_photo_url", length = 500)
    private String taskPhotoUrl;

    @Column(name = "priority", nullable = false, length = 50)
    private String priority;

    @Column(name = "recurrence_type", length = 50)
    private String recurrenceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recurrence_pattern", columnDefinition = "jsonb")
    private Map<String, Object> recurrencePattern;

    @Column(name = "next_due_date")
    private LocalDateTime nextDueDate;

    @Builder.Default
    @Column(name = "requires_photo_proof", nullable = false)
    private Boolean requiresPhotoProof = false;

    @Column(name = "estimated_time_minutes")
    private Integer estimatedTimeMinutes;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "date_completed")
    private LocalDateTime dateCompleted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private GroupMember createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private GroupMember assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
}