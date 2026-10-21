package com.tasklean.api.auth.account.dto;

/**
 * The caller's sign-in methods: whether a password is set and whether a Google account is linked.
 * At least one is always true for an active account (otherwise it couldn't sign in).
 *
 * @param passwordSet  true if the account has a password
 * @param googleLinked true if a Google account is linked
 */
public record ConnectedAccountsResponse(boolean passwordSet, boolean googleLinked) {
}
