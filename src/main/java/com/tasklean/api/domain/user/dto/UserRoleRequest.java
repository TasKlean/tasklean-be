package com.tasklean.api.domain.user.dto;

import com.tasklean.api.domain.user.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleRequest {

    @NotNull
    private UserRole role;
}
