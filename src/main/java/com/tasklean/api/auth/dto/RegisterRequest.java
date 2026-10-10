package com.tasklean.api.auth.dto;

import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    // @ValidPassword also reports an empty/missing password, so no separate @NotBlank here —
    // one constraint owns the whole policy and keeps the UI and API messages identical.
    @ValidPassword
    private String password;

    @NotBlank
    private String name;

    private String middleName;

    @NotBlank
    private String lastName;
}
