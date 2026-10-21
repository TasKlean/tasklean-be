package com.tasklean.api.auth.account.dto;

import lombok.*;

/**
 * Request body for "deactivate account". The password is required for an account that has one
 * (re-authentication) and ignored for a Google-only account, which the valid session confirms.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeactivateAccountRequest {

    private String password;
}
