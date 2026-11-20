package com.tasklean.api.domain.task.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A new task. Everything editable comes from {@link TaskUpdateRequest}; this adds the household it
 * belongs to, which is the one thing a later edit cannot change. The author is taken from the
 * caller's token, never from the request — a task that named its own creator could be attributed
 * to anybody.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateRequest extends TaskUpdateRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String groupUid;

    /** An uploaded picture of what "done" looks like, signed for this task's household. */
    @Size(max = FieldLimits.UID)
    private String photoUid;
}
