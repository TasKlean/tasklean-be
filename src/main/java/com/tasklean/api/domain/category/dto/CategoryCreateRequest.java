package com.tasklean.api.domain.category.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A new category. Everything editable comes from {@link CategoryUpdateRequest}; this adds the
 * household it belongs to, which is the one thing a later edit cannot change.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryCreateRequest extends CategoryUpdateRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String groupUid;
}
