package com.tasklean.api.security;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSecurityTest {

    private final AccountSecurity accountSecurity = new AccountSecurity();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String uid) {
        AuthPrincipal principal = new AuthPrincipal(1L, uid, "user@example.com", UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test
    void isSelf_matchingUid_returnsTrue() {
        authenticateAs("usr-alice");

        assertThat(accountSecurity.isSelf("usr-alice")).isTrue();
    }

    @Test
    void isSelf_differentUid_returnsFalse() {
        authenticateAs("usr-alice");

        assertThat(accountSecurity.isSelf("usr-bob")).isFalse();
    }

    @Test
    void isSelf_noAuthentication_returnsFalse() {
        assertThat(accountSecurity.isSelf("usr-alice")).isFalse();
    }

    @Test
    void isSelf_nonAuthPrincipal_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousString", null));

        assertThat(accountSecurity.isSelf("usr-alice")).isFalse();
    }
}
