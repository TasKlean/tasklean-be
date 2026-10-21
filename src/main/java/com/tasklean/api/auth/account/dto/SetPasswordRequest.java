package com.tasklean.api.auth.account.dto;

import com.tasklean.api.common.validation.ValidPassword;
import lombok.*;

/** Request body for "set password" on a Google-only account: the chosen new password. */
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
}
