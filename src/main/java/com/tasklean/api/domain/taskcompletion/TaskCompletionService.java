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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskCompletionService {

    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskRepository taskRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final Clock clock;

    public List<TaskCompletionResponse> getCompletionsByTask(Long taskId) {
        return taskCompletionRepository.findByTaskIdTaskOrderByDateCompletedDesc(taskId).stream()
                .map(TaskCompletionResponse::from)
                .toList();
    }

    public List<TaskCompletionResponse> getCompletionsByMember(Long groupMemberId) {
        return taskCompletionRepository.findByCompletedByIdGroupMemberOrderByDateCompletedDesc(groupMemberId).stream()
                .map(TaskCompletionResponse::from)
                .toList();
    }

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
        return TaskCompletionResponse.from(taskCompletionRepository.save(completion));
    }
}
