package com.tasklean.api.domain.category.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Which household's categories to list. Bound from the query string as an object rather than as a
 * loose parameter, so a missing or over-long value is a 400 from {@code @Valid} — a constraint on
 * a bare {@code @RequestParam} surfaces as a 500 instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryQueryRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String groupUid;
}
