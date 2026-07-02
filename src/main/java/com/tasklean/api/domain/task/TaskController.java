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

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.createTask(request)));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTaskByUid(uid)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasksByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTasksByGroup(groupId)));
    }

    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable String uid, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.updateTask(uid, request)));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String uid) {
        taskService.deleteTask(uid);
        return ResponseEntity.ok(ApiResponse.success("Task deleted", null));
    }
}
