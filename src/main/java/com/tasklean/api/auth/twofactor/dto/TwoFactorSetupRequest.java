package com.tasklean.api.auth.twofactor.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for starting two-factor setup. The password re-authenticates the caller; it is
 * required for an account that has one and ignored for a Google-only account, which the valid
 * session confirms.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorSetupRequest {

    @Size(max = FieldLimits.PASSWORD_INPUT)
    private String password;
}
