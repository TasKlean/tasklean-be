package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.task.Task;
import com.tasklean.api.domain.task.TaskRepository;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionRequest;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Records and queries task completions — the history of who completed which task
 * and when, with optional photo proof and notes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCompletionService {

    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskRepository taskRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final Clock clock;

    /**
     * Returns the completion history for a task, newest first.
     *
     * @param taskId the task id
     * @return the task's completions
     */
    public List<TaskCompletionResponse> getCompletionsByTask(Long taskId) {
        return taskCompletionRepository.findByTaskIdTaskOrderByDateCompletedDesc(taskId).stream()
                .map(TaskCompletionResponse::from)
                .toList();
    }

    /**
     * Returns the completions recorded by a group member, newest first.
     *
     * @param groupMemberId the group member id
     * @return the member's completions
     */
    public List<TaskCompletionResponse> getCompletionsByMember(Long groupMemberId) {
        return taskCompletionRepository.findByCompletedByIdGroupMemberOrderByDateCompletedDesc(groupMemberId).stream()
                .map(TaskCompletionResponse::from)
                .toList();
    }

    /**
     * Records a completion of a task by a group member.
     *
     * @param request the completion details (task, member, optional photo/note)
     * @return the recorded completion
     * @throws ResourceNotFoundException if the task or group member does not exist
     */
    @Transactional
    public TaskCompletionResponse createCompletion(TaskCompletionRequest request) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.TASK_NOT_FOUND));
        GroupMember member = groupMemberRepository.findById(request.getGroupMemberId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_MEMBER_NOT_FOUND));

        TaskCompletion completion = TaskCompletion.builder()
                .task(task)
                .completedBy(member)
                .completionPhotoUrl(request.getCompletionPhotoUrl())
                .completionNote(request.getCompletionNote())
                .dateCompleted(LocalDateTime.now(clock))
                .build();
        TaskCompletion saved = taskCompletionRepository.save(completion);
        log.info("Task completion recorded: id={} task={} member={}", saved.getIdTaskCompletion(), task.getIdTask(), member.getIdGroupMember());
        return TaskCompletionResponse.from(saved);
    }
}
