package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.groupmember.GroupMember;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_completion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_task_completion")
    private Long idTaskCompletion;

    @Column(name = "completion_photo_url", length = 500)
    private String completionPhotoUrl;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @Column(name = "date_completed", nullable = false)
    private LocalDateTime dateCompleted;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember completedBy;
}