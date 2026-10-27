package com.tasklean.api.auth.twofactor.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for an operation that weakens or replaces an active second factor (turning it off,
 * regenerating recovery codes): both the account password and a current second-factor code, so
 * neither a stolen session nor a stolen password is enough on its own. The password is required
 * for an account that has one and ignored for a Google-only account.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorConfirmRequest {

    @Size(max = FieldLimits.PASSWORD_INPUT)
    private String password;

    @NotBlank(message = "Code is required")
    @Size(min = 1, max = FieldLimits.CODE)
    private String code;
}
