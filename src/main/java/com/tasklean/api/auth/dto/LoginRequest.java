package com.tasklean.api.auth.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank
    @Email
    @Size(min = 1, max = FieldLimits.EMAIL)
    private String email;

    @NotBlank
    @Size(min = 1, max = FieldLimits.PASSWORD_INPUT)
    private String password;
}
