package com.tasklean.api.domain.task;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.task.dto.TaskRequest;
import com.tasklean.api.domain.task.dto.TaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for tasks — create, fetch, list by group, update, and delete.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Creates a task.
     *
     * @param request the task details
     * @return {@code 201 Created} with the created task
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.createTask(request)));
    }

    /**
     * Fetches a task by public UID.
     *
     * @param uid the task's public UID
     * @return {@code 200 OK} with the task
     */
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTaskByUid(uid)));
    }

    /**
     * Lists a group's active tasks.
     *
     * @param groupId the group id
     * @return {@code 200 OK} with the tasks
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasksByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTasksByGroup(groupId)));
    }

    /**
     * Updates a task.
     *
     * @param uid     the task's public UID
     * @param request the new task details
     * @return {@code 200 OK} with the updated task
     */
    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable String uid, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.updateTask(uid, request)));
    }

    /**
     * Soft-deletes a task.
     *
     * @param uid the task's public UID
     * @return {@code 200 OK} with a confirmation message
     */
    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String uid) {
        taskService.deleteTask(uid);
        return ResponseEntity.ok(ApiResponse.success("Task deleted", null));
    }
}
