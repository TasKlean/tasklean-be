package com.tasklean.api.auth.account.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for "set password" on a Google-only account: the chosen new password, plus a
 * current second-factor code when the account has two-factor on.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetPasswordRequest {

    // @ValidPassword also reports an empty/missing password, so no separate @NotBlank —
    // the same policy the UI and register enforce.
    @ValidPassword
    private String newPassword;

    // Only required when the account has two-factor on, which the service checks - an account
    // without it leaves this null.
    @Size(max = FieldLimits.CODE)
    private String code;
}
