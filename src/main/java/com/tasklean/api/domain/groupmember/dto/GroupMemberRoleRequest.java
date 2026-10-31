package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.domain.groupmember.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberRoleRequest {

    @NotNull
    private GroupRole role;
}
