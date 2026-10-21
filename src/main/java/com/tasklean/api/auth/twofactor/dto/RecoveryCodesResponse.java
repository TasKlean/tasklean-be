package com.tasklean.api.auth.twofactor.dto;

import java.util.List;

/**
 * A freshly generated set of recovery codes. Only ever returned once - they are stored hashed, so
 * they cannot be shown again.
 *
 * @param recoveryCodes the single-use codes, in the order they should be displayed
 */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
