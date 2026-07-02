package com.tasklean.api.domain.groupmember.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank
    private String role;
}
