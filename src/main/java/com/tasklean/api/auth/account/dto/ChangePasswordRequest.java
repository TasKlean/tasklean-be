package com.tasklean.api.auth.account.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for "change password": the current password, the chosen new password, and a
 * current second-factor code when the account has two-factor on.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Size(min = 1, max = FieldLimits.PASSWORD_INPUT)
    private String currentPassword;

    // @ValidPassword also reports an empty/missing password, so no separate @NotBlank —
    // the same policy the UI and register enforce.
    @ValidPassword
    private String newPassword;

    // Only required when the account has two-factor on, which the service checks - an account
    // without it leaves this null.
    @Size(max = FieldLimits.CODE)
    private String code;
}
