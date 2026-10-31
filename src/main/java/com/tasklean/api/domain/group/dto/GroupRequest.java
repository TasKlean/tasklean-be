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

    /** The uid of an upload of yours, already PUT to the bucket; null clears the picture. */
    @Size(max = FieldLimits.UID)
    private String photoUid;
}
