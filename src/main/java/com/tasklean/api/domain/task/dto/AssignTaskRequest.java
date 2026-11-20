package com.tasklean.api.domain.task.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignTaskRequest {

    /**
     * Who the chore is for, by membership UID. Absent or null releases it back to the household
     * pool, which is the only way to put a chore down without giving it to somebody else.
     */
    @Size(max = FieldLimits.UID)
    private String memberUid;
}
