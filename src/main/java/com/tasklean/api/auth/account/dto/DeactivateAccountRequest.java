package com.tasklean.api.auth.account.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for "deactivate account". The password is required for an account that has one
 * (re-authentication) and ignored for a Google-only account, which the valid session confirms.
 * A current second-factor code is required when the account has two-factor on.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeactivateAccountRequest {

    @Size(max = FieldLimits.PASSWORD_INPUT)
    private String password;

    // Only required when the account has two-factor on, which the service checks - an account
    // without it leaves this null.
    @Size(max = FieldLimits.CODE)
    private String code;
}
