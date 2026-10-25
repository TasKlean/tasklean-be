package com.tasklean.api.auth.refresh;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.email.EmailTemplates;
import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.device.Device;
import com.tasklean.api.domain.device.DeviceLoginOutcome;
import com.tasklean.api.domain.device.DeviceService;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private EmailService emailService;

    @Spy
    private EmailTemplates emailTemplates = new EmailTemplates("http://localhost:3000");

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User buildUser() {
        return User.builder()
                .idUser(1L)
                .uid("usr-test-123")
                .email("test@example.com")
                .name("Test")
                .isActive(true)
                .isEmailVerified(true)
                .build();
    }

    private Device buildDevice(Long id) {
        return Device.builder()
                .idDevice(id)
                .deviceName("Chrome on Windows")
                .ipAddress("203.0.113.5")
                .isActive(true)
                .build();
    }

    // --- createRefreshToken ---

    @Test
    void createRefreshToken_noDeviceId_persistsTokenWithoutDeviceOrEmail() {
        User user = buildUser();

        // A login carrying no device id resolves to no device.
        when(deviceService.resolveLoginDevice(user)).thenReturn(new DeviceLoginOutcome(null, false));
        when(jwtConfig.getRefreshExpiration()).thenReturn(1209600000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(user);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getToken()).isNotEqualTo(rawToken);
        assertThat(saved.getDevice()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(13));
        assertThat(saved.getIsRevoked()).isFalse();

        verify(emailService, never()).send(any(), any());
    }

    @Test
    void createRefreshToken_newDevice_bindsDeviceAndAlertsOwner() {
        User user = buildUser();
        Device device = buildDevice(5L);

        when(deviceService.resolveLoginDevice(user)).thenReturn(new DeviceLoginOutcome(device, true));
        when(jwtConfig.getRefreshExpiration()).thenReturn(1209600000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        refreshTokenService.createRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getDevice()).isEqualTo(device);

        verify(emailService).send("test@example.com",
                emailTemplates.newSignIn("Chrome on Windows", "203.0.113.5"));
        verify(auditLogService).recordAccountEvent(eq(1L), eq(AuditAction.NEW_DEVICE_LOGIN), anyString());
    }

    @Test
    void createRefreshToken_knownDevice_bindsDeviceWithoutEmail() {
        User user = buildUser();
        Device device = buildDevice(5L);

        when(deviceService.resolveLoginDevice(user)).thenReturn(new DeviceLoginOutcome(device, false));
        when(jwtConfig.getRefreshExpiration()).thenReturn(1209600000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        refreshTokenService.createRefreshToken(user);

        verify(emailService, never()).send(any(), any());
        verify(auditLogService, never()).recordAccountEvent(any(), eq(AuditAction.NEW_DEVICE_LOGIN), anyString());
    }

    // --- refresh ---

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() {
        User user = buildUser();
        RefreshToken existing = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(3))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-refresh-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(existing));
        when(jwtConfig.getRefreshExpiration()).thenReturn(1209600000L);
        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        AuthResponse response = refreshTokenService.refresh(request);

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(existing.getIsRevoked()).isTrue();
        // Rotation doesn't go through device resolution (keeps the same device).
        verify(deviceService, never()).resolveLoginDevice(any());
    }

    @Test
    void refresh_withDevice_keepsDeviceAndRecordsActivity() {
        User user = buildUser();
        Device device = buildDevice(5L);
        device.setLastUsed(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        RefreshToken existing = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .device(device)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(3))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-refresh-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(existing));
        when(jwtConfig.getRefreshExpiration()).thenReturn(1209600000L);
        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        refreshTokenService.refresh(request);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        // Second save is the new token; it stays on the same device.
        RefreshToken issued = captor.getAllValues().get(1);
        assertThat(issued.getDevice()).isEqualTo(device);
        // The touch is DeviceService's job, so last-used and IP cannot drift apart.
        verify(deviceService).recordActivity(device);
    }

    @Test
    void refresh_deactivatedUser_revokesAllAndThrows() {
        User user = buildUser();
        user.setIsActive(false);
        RefreshToken existing = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(3))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-refresh-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.refresh(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("deactivated");

        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void refresh_expiredToken_revokesAndThrows() {
        User user = buildUser();
        RefreshToken expired = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("expired-raw-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.refresh(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expired");

        assertThat(expired.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(expired);
    }

    @Test
    void refresh_revokedToken_throws() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("revoked-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refresh(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refresh_unknownToken_throws() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("nonexistent-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refresh(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // --- logout (per-device) ---

    @Test
    void logout_withDevice_revokesThatDeviceSessions() {
        User user = buildUser();
        Device device = buildDevice(5L);
        RefreshToken token = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .device(device)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(3))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        refreshTokenService.logout(request);

        verify(refreshTokenRepository).revokeAllByDeviceId(5L);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        verify(auditLogService).recordAccountEvent(eq(1L), eq(AuditAction.LOGOUT), anyString());
    }

    @Test
    void logout_legacyNoDevice_revokesOnlyThatToken() {
        User user = buildUser();
        RefreshToken token = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(3))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        refreshTokenService.logout(request);

        assertThat(token.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
        verify(refreshTokenRepository, never()).revokeAllByDeviceId(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void logout_invalidToken_throws() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("bad-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.logout(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // --- logoutAll ---

    @Test
    void logoutAll_validToken_revokesAllUserTokensAndAudits() {
        User user = buildUser();
        RefreshToken token = RefreshToken.builder()
                .idRefreshToken(1L)
                .user(user)
                .token("hashed-value")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(3))
                .isRevoked(false)
                .build();

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        refreshTokenService.logoutAll(request);

        verify(refreshTokenRepository).revokeAllByUserId(1L);
        verify(auditLogService).recordAccountEvent(eq(1L), eq(AuditAction.ALL_SESSIONS_REVOKED), anyString());
    }

    @Test
    void logoutAll_invalidToken_throws() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("bad-token");

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.logoutAll(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid refresh token");
    }
}
