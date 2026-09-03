package com.tasklean.api.domain.task;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.category.Category;
import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.dto.TaskRequest;
import com.tasklean.api.domain.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages tasks within a group — lookup by UID, listing, creation, updates, and
 * soft-deletion, including optional assignee and category resolution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns a task by its public UID.
     *
     * @param uid the task's public UID
     * @return the task
     * @throws ResourceNotFoundException if no task has that UID
     */
    public TaskResponse getTaskByUid(String uid) {
        Task task = taskRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        return TaskResponse.from(task);
    }

    /**
     * Returns the active tasks for a group.
     *
     * @param groupId the group id
     * @return the group's active tasks
     */
    public List<TaskResponse> getTasksByGroup(Long groupId) {
        return taskRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * Creates a task in a group, resolving its creator and optional assignee/category.
     *
     * @param request the task details
     * @return the created task
     * @throws ResourceNotFoundException if the group, creator, assignee, or category does not exist
     */
    @Transactional
    public TaskResponse createTask(TaskRequest request) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
        GroupMember createdBy = groupMemberRepository.findById(request.getCreatedById())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));

        Task task = Task.builder()
                .uid(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .taskPhotoUrl(request.getTaskPhotoUrl())
                .priority(request.getPriority())
                .recurrenceType(request.getRecurrenceType())
                .recurrencePattern(request.getRecurrencePattern())
                .nextDueDate(request.getNextDueDate())
                .requiresPhotoProof(request.getRequiresPhotoProof() != null ? request.getRequiresPhotoProof() : false)
                .estimatedTimeMinutes(request.getEstimatedTimeMinutes())
                .status(request.getStatus())
                .isActive(true)
                .group(group)
                .createdBy(createdBy)
                .build();

        if (request.getAssignedToId() != null) {
            task.setAssignedTo(groupMemberRepository.findById(request.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ASSIGNED_MEMBER_NOT_FOUND)));
        }
        if (request.getCategoryId() != null) {
            task.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND)));
        }

        Task saved = taskRepository.save(task);
        // Significant business event → INFO. Log the business identifier (uid), never full entities or PII.
        log.info("Task created: uid={} group={}", saved.getUid(), group.getIdGroup());
        auditLogService.record(AuditEntityType.TASK, saved.getIdTask(), AuditAction.CREATE,
                "Task \"" + saved.getName() + "\" created", group);
        return TaskResponse.from(saved);
    }

    /**
     * Updates a task's fields, re-resolving or clearing its assignee and category.
     *
     * @param uid     the task's public UID
     * @param request the new task details
     * @return the updated task
     * @throws ResourceNotFoundException if the task, assignee, or category does not exist
     */
    @Transactional
    public TaskResponse updateTask(String uid, TaskRequest request) {
        Task task = taskRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));

        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setTaskPhotoUrl(request.getTaskPhotoUrl());
        task.setPriority(request.getPriority());
        task.setRecurrenceType(request.getRecurrenceType());
        task.setRecurrencePattern(request.getRecurrencePattern());
        task.setNextDueDate(request.getNextDueDate());
        task.setRequiresPhotoProof(request.getRequiresPhotoProof() != null ? request.getRequiresPhotoProof() : false);
        task.setEstimatedTimeMinutes(request.getEstimatedTimeMinutes());
        task.setStatus(request.getStatus());

        if (request.getAssignedToId() != null) {
            task.setAssignedTo(groupMemberRepository.findById(request.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ASSIGNED_MEMBER_NOT_FOUND)));
        } else {
            task.setAssignedTo(null);
        }

        if (request.getCategoryId() != null) {
            task.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.CATEGORY_NOT_FOUND)));
        } else {
            task.setCategory(null);
        }

        Task saved = taskRepository.save(task);
        auditLogService.record(AuditEntityType.TASK, saved.getIdTask(), AuditAction.UPDATE,
                "Task \"" + saved.getName() + "\" updated", saved.getGroup());
        return TaskResponse.from(saved);
    }

    /**
     * Soft-deletes a task (sets it inactive).
     *
     * @param uid the task's public UID
     * @throws ResourceNotFoundException if no task has that UID
     */
    @Transactional
    public void deleteTask(String uid) {
        Task task = taskRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        task.setIsActive(false);
        taskRepository.save(task);
        log.info("Task soft-deleted: uid={}", uid);
        auditLogService.record(AuditEntityType.TASK, task.getIdTask(), AuditAction.DELETE,
                "Task \"" + task.getName() + "\" deleted", task.getGroup());
    }
}
