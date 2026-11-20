package com.tasklean.api.domain.taskcompletion;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.taskcompletion.dto.TaskCompletionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One entry in a chore's history, addressed by its own id. Recording an entry is an action on the
 * chore and lives on {@code /api/tasks/{uid}}; this controller owns only what is done to an entry
 * once it exists - reading it, and taking it back.
 */
@Tag(name = "Task history", description = "Individual entries in a chore's history")
@RestController
@RequestMapping("/api/task-completions")
@RequiredArgsConstructor
public class TaskCompletionController {

    private final TaskCompletionService taskCompletionService;

    /**
     * Returns one entry from a chore's history. Any member of the household may read it.
     *
     * @param id the entry's id
     * @return {@code 200 OK} with the entry
     */
    @PreAuthorize("@taskCompletionSecurity.canView(#id)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskCompletionResponse>> getCompletion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.getCompletion(id)));
    }

    /**
     * Takes an entry back, putting the chore's schedule where it was before it. Whoever the entry
     * is attributed to may, as may the household's admin; only the newest entry can be undone.
     *
     * @param id the entry's id
     * @return {@code 200 OK} with the entry and the chore's restored state
     */
    @PreAuthorize("@taskCompletionSecurity.canUndo(#id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskCompletionResponse>> undoCompletion(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(taskCompletionService.undo(id)));
    }
}
