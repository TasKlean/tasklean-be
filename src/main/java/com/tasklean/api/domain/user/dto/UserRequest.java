package com.tasklean.api.domain.user.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A profile edit: the name parts, and deliberately nothing else. A photo URL is not accepted - an
 * image of ours is attached by upload uid, never pasted as a link, which is the change V25 already
 * made for group photos. The sign-in address is not a profile field either.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.NAME)
    private String name;

    @Size(max = FieldLimits.NAME)
    private String middleName;

    @NotBlank
    @Size(min = 1, max = FieldLimits.NAME)
    private String lastName;
}
