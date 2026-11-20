package com.tasklean.api.domain.taskcompletion.dto;

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
public class SkipTaskRequest {

    @Size(max = FieldLimits.COMPLETION_NOTE)
    private String note;
}
