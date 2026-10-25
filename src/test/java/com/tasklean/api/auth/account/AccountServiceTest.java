package com.tasklean.api.auth.account;

import com.tasklean.api.auth.GoogleIdentity;
import com.tasklean.api.auth.GoogleOAuthService;
import com.tasklean.api.auth.SessionRotator;
import com.tasklean.api.auth.account.dto.*;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.email.EmailTemplates;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SessionRotator sessionRotator;
    @Mock
    private GoogleOAuthService googleOAuthService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private EmailService emailService;
    @Spy
    private EmailTemplates emailTemplates = new EmailTemplates("http://localhost:3000");
    @InjectMocks
    private AccountService accountService;

    private static final Long USER_ID = 1L;

    private User user(String passwordHash, String googleSub) {
        return User.builder().idUser(USER_ID).uid("usr-1").email("bob@example.com")
                .passwordHash(passwordHash).googleSub(googleSub).isActive(true).build();
    }

    // --- changePassword ---

    @Test
    void changePassword_success_rotatesSessionAndReturnsNewTokens() {
        User user = user("$old", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$new");
        when(sessionRotator.rotateCurrentSession(user)).thenReturn(AuthResponse.from(user, "access", "refresh"));

        AuthResponse response = accountService.changePassword(USER_ID,
                new ChangePasswordRequest("current", "NewPass1!"));

        assertThat(response.getToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(user.getPasswordHash()).isEqualTo("$new");
        verify(sessionRotator).rotateCurrentSession(user);
        verify(emailService).send("bob@example.com", emailTemplates.passwordChanged());
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.PASSWORD_CHANGED), anyString());
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsAndDoesNotChange() {
        User user = user("$old", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$old")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(USER_ID,
                new ChangePasswordRequest("wrong", "NewPass1!")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).save(any());
        verify(sessionRotator, never()).rotateCurrentSession(any());
    }

    @Test
    void changePassword_googleOnlyAccount_throwsBusinessRule() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null, "sub-1")));

        assertThatThrownBy(() -> accountService.changePassword(USER_ID,
                new ChangePasswordRequest("current", "NewPass1!")))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- setPassword ---

    @Test
    void setPassword_googleOnlyAccount_setsAndRotates() {
        User user = user(null, "sub-1");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$new");
        when(sessionRotator.rotateCurrentSession(user)).thenReturn(AuthResponse.from(user, "access", "refresh"));

        AuthResponse response = accountService.setPassword(USER_ID, new SetPasswordRequest("NewPass1!"));

        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(user.getPasswordHash()).isEqualTo("$new");
        verify(sessionRotator).rotateCurrentSession(user);
        verify(emailService).send("bob@example.com", emailTemplates.passwordSet());
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.PASSWORD_SET), anyString());
    }

    @Test
    void setPassword_accountAlreadyHasPassword_throwsBusinessRule() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", null)));

        assertThatThrownBy(() -> accountService.setPassword(USER_ID, new SetPasswordRequest("NewPass1!")))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    // --- getConnections ---

    @Test
    void getConnections_reportsPasswordAndGoogleState() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", "sub-1")));

        ConnectedAccountsResponse response = accountService.getConnections(USER_ID);

        assertThat(response.passwordSet()).isTrue();
        assertThat(response.googleLinked()).isTrue();
    }

    // --- linkGoogle ---

    @Test
    void linkGoogle_success_setsSubAndNotifies() {
        User user = user("$old", null);
        GoogleAuthRequest request = new GoogleAuthRequest("id-token", "nonce");
        when(googleOAuthService.verifyLinkToken(request)).thenReturn(new GoogleIdentity("sub-new", "bob@example.com"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());

        ConnectedAccountsResponse response = accountService.linkGoogle(USER_ID, request);

        assertThat(user.getGoogleSub()).isEqualTo("sub-new");
        assertThat(response.googleLinked()).isTrue();
        verify(emailService).send("bob@example.com", emailTemplates.googleLinked());
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.GOOGLE_LINKED), anyString());
    }

    @Test
    void linkGoogle_alreadyLinked_throwsBusinessRule() {
        GoogleAuthRequest request = new GoogleAuthRequest("id-token", "nonce");
        when(googleOAuthService.verifyLinkToken(request)).thenReturn(new GoogleIdentity("sub-new", "bob@example.com"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", "sub-existing")));

        assertThatThrownBy(() -> accountService.linkGoogle(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void linkGoogle_emailDiffersFromAccount_throwsBusinessRule() {
        GoogleAuthRequest request = new GoogleAuthRequest("id-token", "nonce");
        when(googleOAuthService.verifyLinkToken(request)).thenReturn(new GoogleIdentity("sub-new", "someone-else@gmail.com"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", null)));

        assertThatThrownBy(() -> accountService.linkGoogle(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByGoogleSub(any());
    }

    @Test
    void linkGoogle_subOwnedByAnotherUser_throwsBusinessRule() {
        GoogleAuthRequest request = new GoogleAuthRequest("id-token", "nonce");
        when(googleOAuthService.verifyLinkToken(request)).thenReturn(new GoogleIdentity("sub-new", "bob@example.com"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", null)));
        when(userRepository.findByGoogleSub("sub-new"))
                .thenReturn(Optional.of(User.builder().idUser(999L).build()));

        assertThatThrownBy(() -> accountService.linkGoogle(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    // --- unlinkGoogle ---

    @Test
    void unlinkGoogle_success_clearsSubAndNotifies() {
        User user = user("$old", "sub-1");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        ConnectedAccountsResponse response = accountService.unlinkGoogle(USER_ID);

        assertThat(user.getGoogleSub()).isNull();
        assertThat(response.googleLinked()).isFalse();
        verify(emailService).send("bob@example.com", emailTemplates.googleUnlinked());
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.GOOGLE_UNLINKED), anyString());
    }

    @Test
    void unlinkGoogle_notLinked_throwsBusinessRule() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", null)));

        assertThatThrownBy(() -> accountService.unlinkGoogle(USER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unlinkGoogle_noPasswordSet_throwsLockoutGuard() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null, "sub-1")));

        assertThatThrownBy(() -> accountService.unlinkGoogle(USER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    // --- deactivate ---

    @Test
    void deactivate_passwordAccount_withCorrectPassword_deactivatesAndRevokes() {
        User user = user("$old", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$old")).thenReturn(true);

        accountService.deactivate(USER_ID, new DeactivateAccountRequest("pw"));

        assertThat(user.getIsActive()).isFalse();
        verify(refreshTokenService).revokeAllUserTokens(user);
        verify(emailService).send("bob@example.com", emailTemplates.accountDeactivated());
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.ACCOUNT_DELETED), anyString());
    }

    @Test
    void deactivate_passwordAccount_wrongPassword_throwsAndStaysActive() {
        User user = user("$old", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "$old")).thenReturn(false);

        assertThatThrownBy(() -> accountService.deactivate(USER_ID, new DeactivateAccountRequest("bad")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getIsActive()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivate_passwordAccount_missingPassword_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$old", null)));

        assertThatThrownBy(() -> accountService.deactivate(USER_ID, new DeactivateAccountRequest(null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void deactivate_googleOnlyAccount_noPasswordRequired() {
        User user = user(null, "sub-1");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        accountService.deactivate(USER_ID, new DeactivateAccountRequest(null));

        assertThat(user.getIsActive()).isFalse();
        verify(refreshTokenService).revokeAllUserTokens(user);
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.ACCOUNT_DELETED), anyString());
    }
}
