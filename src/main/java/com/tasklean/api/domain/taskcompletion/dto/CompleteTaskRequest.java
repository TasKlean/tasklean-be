package com.tasklean.api.domain.taskcompletion.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteTaskRequest {

    @Size(max = FieldLimits.COMPLETION_NOTE)
    private String note;

    /**
     * How long it actually took. Left out, the task's estimate is snapshotted instead, so the
     * share is never silently zero for a chore nobody timed.
     */
    @Min(1)
    @Max(FieldLimits.TASK_MINUTES)
    private Integer minutes;

    /**
     * Proof the round was done, signed for this task. Required when the chore asks for proof,
     * and refused as a 409 otherwise - a flag nothing enforces is not a rule.
     */
    @Size(max = FieldLimits.UID)
    private String photoUid;
}
