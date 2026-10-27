package com.tasklean.api.auth.passwordreset.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Request body for "forgot password": the email to send a reset link to. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Size(min = 1, max = FieldLimits.EMAIL)
    private String email;
}
