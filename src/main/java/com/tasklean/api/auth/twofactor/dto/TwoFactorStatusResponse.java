package com.tasklean.api.auth.twofactor.dto;

import java.time.LocalDateTime;

/**
 * The caller's two-factor state, enough for the settings screen to decide what to show.
 *
 * @param enabled                 true if a confirmed second factor is required at sign-in
 * @param pendingSetup            true if setup was started but never confirmed, so the client can
 *                                offer to resume it
 * @param confirmedAt             when the factor was confirmed; null while it isn't enabled
 * @param recoveryCodesRemaining  how many unused recovery codes are left; 0 when not enabled
 */
public record TwoFactorStatusResponse(boolean enabled, boolean pendingSetup, LocalDateTime confirmedAt,
                                      long recoveryCodesRemaining) {
}
