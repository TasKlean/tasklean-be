package com.tasklean.api.domain.group.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.GROUP_NAME)
    private String name;

    @Size(max = FieldLimits.DESCRIPTION)
    private String description;

    @Size(max = FieldLimits.PHOTO_URL)
    private String photoUrl;
}
