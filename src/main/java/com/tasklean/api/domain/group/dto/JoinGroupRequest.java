package com.tasklean.api.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinGroupRequest {

    @NotBlank
    private String inviteCode;
}
