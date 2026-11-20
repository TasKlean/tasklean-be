package com.tasklean.api.domain.task;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.task.dto.AssignTaskRequest;
import com.tasklean.api.domain.task.dto.TaskCreateRequest;
import com.tasklean.api.domain.task.dto.TaskResponse;
import com.tasklean.api.domain.task.dto.TaskUpdateRequest;
import com.tasklean.api.domain.taskcompletion.TaskCompletionService;
import com.tasklean.api.domain.taskcompletion.dto.CompleteTaskRequest;
import com.tasklean.api.domain.taskcompletion.dto.SkipTaskRequest;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryPage;
import com.tasklean.api.domain.taskcompletion.dto.TaskHistoryQueryRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for a household's tasks — create, fetch, update, archive and delete, plus the
 * actions on one: doing it, skipping it, taking it, handing it on, and reading its history. Any
 * member may add a chore; only its author or a group admin may change one. The board that lists
 * them is served by the group it belongs to.
 */
@Tag(name = "Tasks", description = "Chores within a household.")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskCompletionService taskCompletionService;

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
     * Records a chore as done. Membership is the coarse right, which is all an expression can
     * state: whether this chore is the caller's to do depends on who it is assigned to, so
     * {@code TaskCompletionService} refuses somebody else's. An unassigned one is claimed here.
     *
     * @param uid       the task's public UID
     * @param request   the note and, optionally, how long it really took
     * @param principal the caller, from the JWT
     * @return {@code 201 Created} with the entry recorded and the chore's new state
     */
    @PreAuthorize("@taskSecurity.canView(#uid)")
    @PostMapping("/{uid}/complete")
    public ResponseEntity<ApiResponse<TaskCompletionResponse>> completeTask(
            @PathVariable String uid, @Valid @RequestBody CompleteTaskRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                taskCompletionService.complete(uid, request, principal.userId())));
    }

    /**
     * Records a chore as skipped - this round happened and nobody did it. Same rights as doing it.
     *
     * @param uid       the task's public UID
     * @param request   the note saying why
     * @param principal the caller, from the JWT
     * @return {@code 201 Created} with the entry recorded and the chore's new state
     */
    @PreAuthorize("@taskSecurity.canView(#uid)")
    @PostMapping("/{uid}/skip")
    public ResponseEntity<ApiResponse<TaskCompletionResponse>> skipTask(
            @PathVariable String uid, @Valid @RequestBody SkipTaskRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                taskCompletionService.skip(uid, request, principal.userId())));
    }

    /**
     * Takes an unassigned chore. Any member may, since volunteering needs no right over the task.
     *
     * @param uid       the task's public UID
     * @param principal the caller, from the JWT
     * @return {@code 200 OK} with the task, now the caller's
     */
    @PreAuthorize("@taskSecurity.canView(#uid)")
    @PostMapping("/{uid}/claim")
    public ResponseEntity<ApiResponse<TaskResponse>> claimTask(
            @PathVariable String uid, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                taskService.claimTask(uid, principal.userId())));
    }

    /**
     * Hands a chore to a member of its household, or releases it when no member is named. The
     * author or a group admin, not any member: deciding who does a chore is not the same as doing
     * it, and a PUT so that releasing it is the same call with nothing in it.
     *
     * @param uid     the task's public UID
     * @param request who to assign it to, or nothing to release it
     * @return {@code 200 OK} with the task and its new assignee
     */
    @PreAuthorize("@taskSecurity.canEdit(#uid)")
    @PutMapping("/{uid}/assignee")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTask(
            @PathVariable String uid, @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.assignTask(uid, request)));
    }

    /**
     * Returns a page of a chore's history, most recently due first. Any member of the household.
     *
     * @param uid   the task's public UID
     * @param query the cursor and page size
     * @return {@code 200 OK} with the page
     */
    @PreAuthorize("@taskSecurity.canView(#uid)")
    @GetMapping("/{uid}/completions")
    public ResponseEntity<ApiResponse<TaskHistoryPage>> getTaskHistory(
            @PathVariable String uid, @Valid @ParameterObject TaskHistoryQueryRequest query) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.getHistory(uid, query)));
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
