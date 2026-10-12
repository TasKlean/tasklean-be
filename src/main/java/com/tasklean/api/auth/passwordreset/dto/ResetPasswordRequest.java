package com.tasklean.api.auth.passwordreset.dto;

import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Request body for "reset password": the emailed token and the chosen new password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    // @ValidPassword also reports an empty/missing password, so no separate @NotBlank —
    // the same policy the UI and register enforce.
    @ValidPassword
    private String newPassword;
}
