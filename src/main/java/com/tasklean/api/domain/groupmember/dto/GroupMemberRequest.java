package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.domain.groupmember.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long groupId;

    @NotNull
    private GroupRole role;
}
