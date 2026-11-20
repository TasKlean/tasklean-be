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
public class TaskHistoryQueryRequest {

    /** Opaque, from the previous page's {@code nextCursor}. Absent for the first page. */
    @Size(max = FieldLimits.CURSOR)
    private String cursor;

    @Min(1)
    @Max(FieldLimits.PAGE_SIZE)
    private Integer limit = FieldLimits.TASK_PAGE_SIZE;
}
