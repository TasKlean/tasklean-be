package com.tasklean.api.domain.tag.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A new tag. Everything editable comes from {@link TagUpdateRequest}; this adds the household it
 * belongs to. Any member may create one — a tag is a label somebody found useful, not a setting.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagCreateRequest extends TagUpdateRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String groupUid;
}
