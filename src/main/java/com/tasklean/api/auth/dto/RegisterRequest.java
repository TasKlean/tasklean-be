package com.tasklean.api.auth.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank
    @Email
    @Size(min = 1, max = FieldLimits.EMAIL)
    private String email;

    // @ValidPassword also reports an empty/missing password, so no separate @NotBlank here —
    // one constraint owns the whole policy and keeps the UI and API messages identical.
    @ValidPassword
    private String password;

    @NotBlank
    @Size(min = 1, max = FieldLimits.NAME)
    private String name;

    @Size(max = FieldLimits.NAME)
    private String middleName;

    @NotBlank
    @Size(min = 1, max = FieldLimits.NAME)
    private String lastName;
}
