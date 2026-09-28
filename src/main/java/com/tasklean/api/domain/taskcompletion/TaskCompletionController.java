package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionRequest;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for task completions — record a completion and list completions by task or member.
 */
@Tag(name = "Task completions", description = "Immutable records of a task being completed, with optional photo proof.")
@RestController
@RequestMapping("/api/task-completions")
@RequiredArgsConstructor
public class TaskCompletionController {

    private final TaskCompletionService taskCompletionService;

    /**
     * Records a task completion.
     *
     * @param request the completion details (task, member, optional photo/note)
     * @return {@code 201 Created} with the recorded completion
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskCompletionResponse>> createCompletion(
            @Valid @RequestBody TaskCompletionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskCompletionService.createCompletion(request)));
    }

    /**
     * Lists a task's completion history, newest first.
     *
     * @param taskId the task id
     * @return {@code 200 OK} with the completions
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskCompletionResponse>>> getCompletionsByTask(@RequestParam Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.getCompletionsByTask(taskId)));
    }

    /**
     * Lists the completions recorded by a group member, newest first.
     *
     * @param groupMemberId the group member id
     * @return {@code 200 OK} with the completions
     */
    @GetMapping("/member/{groupMemberId}")
    public ResponseEntity<ApiResponse<List<TaskCompletionResponse>>> getCompletionsByMember(
            @PathVariable Long groupMemberId) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.getCompletionsByMember(groupMemberId)));
    }
}
