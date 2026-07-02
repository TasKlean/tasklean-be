package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.category.Category;
import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.dto.TaskRequest;
import com.tasklean.api.domain.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CategoryRepository categoryRepository;

    public TaskResponse getTaskByUid(String uid) {
        Task task = taskRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        return TaskResponse.from(task);
    }

    public List<TaskResponse> getTasksByGroup(Long groupId) {
        return taskRepository.findByGroupIdGroupAndIsActiveTrue(groupId).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse createTask(TaskRequest request) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        GroupMember createdBy = groupMemberRepository.findById(request.getCreatedById())
                .orElseThrow(() -> new ResourceNotFoundException("Group member not found"));

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
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned member not found")));
        }
        if (request.getCategoryId() != null) {
            task.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found")));
        }

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTask(String uid, TaskRequest request) {
        Task task = taskRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

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
                    .orElseThrow(() -> new ResourceNotFoundException("Assigned member not found")));
        } else {
            task.setAssignedTo(null);
        }

        if (request.getCategoryId() != null) {
            task.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found")));
        } else {
            task.setCategory(null);
        }

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(String uid) {
        Task task = taskRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        task.setIsActive(false);
        taskRepository.save(task);
    }
}
