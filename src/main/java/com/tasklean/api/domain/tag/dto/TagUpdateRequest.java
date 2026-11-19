package com.tasklean.api.domain.tag.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * The editable state of a tag. Carries no household, for the same reason a category does not: a
 * tag that could move between them would take every task labelled with it along.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagUpdateRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.LABEL_NAME)
    private String name;

    /** A `#RRGGBB` hex code. The column holds seven characters, so nothing else fits. */
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #2ECC71")
    @Size(max = FieldLimits.COLOR)
    private String color;
}
