package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.groupmember.GroupRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String groupUid;

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String userUid;

    @NotNull
    private GroupRole role;
}
