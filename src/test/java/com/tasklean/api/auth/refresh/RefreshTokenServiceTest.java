package com.tasklean.api.auth.refresh;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

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

    // --- createRefreshToken ---

    @Test
    void createRefreshToken_generatesAndPersistsHashedToken() {
        User user = buildUser();

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(user);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getToken()).isNotEqualTo(rawToken);
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(6));
        assertThat(saved.getIsRevoked()).isFalse();
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
        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        AuthResponse response = refreshTokenService.refresh(request);

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(existing.getIsRevoked()).isTrue();
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

    // --- logout ---

    @Test
    void logout_validToken_revokesAllUserTokens() {
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

        verify(refreshTokenRepository).revokeAllByUserId(1L);
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
}
