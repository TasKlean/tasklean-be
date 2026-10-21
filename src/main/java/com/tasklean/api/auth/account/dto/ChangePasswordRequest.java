package com.tasklean.api.auth.account.dto;

import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Request body for "change password": the current password plus the chosen new password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    // @ValidPassword also reports an empty/missing password, so no separate @NotBlank —
    // the same policy the UI and register enforce.
    @ValidPassword
    private String newPassword;
}
