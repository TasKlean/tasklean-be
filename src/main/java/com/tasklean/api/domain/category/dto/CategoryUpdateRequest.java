package com.tasklean.api.domain.category.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * The editable state of a category. Carries no household: a category cannot move between them,
 * and one that could would take every task classified by it along.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.LABEL_NAME)
    private String name;

    /** A `#RRGGBB` hex code. The column holds seven characters, so nothing else fits. */
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #4ECDC4")
    @Size(max = FieldLimits.COLOR)
    private String color;

    @Size(max = FieldLimits.ICON)
    private String icon;
}
