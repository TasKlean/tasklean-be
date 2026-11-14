package com.tasklean.api.domain.user.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * A profile edit: the name parts and the uid of an uploaded photo. A photo URL is not accepted - an
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

    /**
     * The uid of a user-photo upload of yours, already PUT to the bucket; null clears ours so the
     * Google picture shows again. Only the subject may send it - never an admin editing someone.
     */
    @Size(max = FieldLimits.UID)
    private String photoUid;
}
