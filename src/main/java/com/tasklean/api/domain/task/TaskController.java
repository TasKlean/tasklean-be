package com.tasklean.api.domain.task;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.task.dto.TaskCreateRequest;
import com.tasklean.api.domain.task.dto.TaskResponse;
import com.tasklean.api.domain.task.dto.TaskUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for a household's tasks — create, fetch, update, archive and delete. Any member
 * may add a chore; only its author or a group admin may change one. The board that lists them is
 * served separately, and completing one lives with the completions.
 */
@Tag(name = "Tasks", description = "Chores within a household.")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Creates a task in a household the caller belongs to. The author is the caller.
     *
     * @param request   the task's details and the household it belongs to
     * @param principal the authenticated caller
     * @return {@code 201 Created} with the created task
     */
    @PreAuthorize("@groupSecurity.isMember(#request.groupUid)")
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody TaskCreateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.createTask(request, principal.userId())));
    }

    /**
     * Fetches a task by public UID.
     *
     * @param uid the task's public UID
     * @return {@code 200 OK} with the task
     */
    @PreAuthorize("@taskSecurity.canView(#uid)")
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTaskByUid(uid)));
    }

    /**
     * Updates a task's details.
     *
     * @param uid     the task's public UID
     * @param request the new details
     * @return {@code 200 OK} with the updated task
     */
    @PreAuthorize("@taskSecurity.canEdit(#uid)")
    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable String uid, @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.updateTask(uid, request)));
    }

    /**
     * Retires a task the household has stopped doing, keeping its history.
     *
     * @param uid the task's public UID
     * @return {@code 200 OK} with the archived task
     */
    @PreAuthorize("@taskSecurity.canEdit(#uid)")
    @PostMapping("/{uid}/archive")
    public ResponseEntity<ApiResponse<TaskResponse>> archiveTask(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(taskService.archiveTask(uid)));
    }

    /**
     * Soft-deletes a task.
     *
     * @param uid the task's public UID
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("@taskSecurity.canEdit(#uid)")
    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String uid) {
        taskService.deleteTask(uid);
        return ResponseEntity.ok(ApiResponse.success("Task deleted", null));
    }
}
