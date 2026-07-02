package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionRequest;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task-completions")
@RequiredArgsConstructor
public class TaskCompletionController {

    private final TaskCompletionService taskCompletionService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskCompletionResponse>> createCompletion(
            @Valid @RequestBody TaskCompletionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskCompletionService.createCompletion(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskCompletionResponse>>> getCompletionsByTask(@RequestParam Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.getCompletionsByTask(taskId)));
    }

    @GetMapping("/member/{groupMemberId}")
    public ResponseEntity<ApiResponse<List<TaskCompletionResponse>>> getCompletionsByMember(
            @PathVariable Long groupMemberId) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.getCompletionsByMember(groupMemberId)));
    }
}
