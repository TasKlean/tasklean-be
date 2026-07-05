package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User buildExistingUser() {
        return User.builder()
                .idUser(1L)
                .uid("usr-existing-123")
                .email("bob@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .name("Bob")
                .lastName("Smith")
                .isActive(true)
                .build();
    }

    // --- Register ---

    @Test
    void register_newEmail_createsUserAndReturnsToken() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setName("New");
        request.setLastName("User");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setIdUser(99L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getName()).isEqualTo("New");
    }

    @Test
    void register_hashesPasswordBeforeSaving() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("mypassword");
        request.setName("Test");
        request.setLastName("User");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("mypassword")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void register_generatesUniqueUid() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setName("Test");
        request.setLastName("User");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUid()).isNotBlank();
    }

    @Test
    void register_duplicateEmail_throwsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password123");
        request.setName("Test");
        request.setLastName("User");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    // --- Login ---

    @Test
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("correctPassword");

        User user = buildExistingUser();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("login-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("login-token");
        assertThat(response.getUid()).isEqualTo("usr-existing-123");
        assertThat(response.getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void login_nonExistentEmail_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("password");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("wrongPassword");

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(buildExistingUser()));
        when(passwordEncoder.matches("wrongPassword", "$2a$10$hashedpassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_deactivatedAccount_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("password");

        User deactivated = buildExistingUser();
        deactivated.setIsActive(false);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_oauthOnlyAccount_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("password");

        User oauthUser = buildExistingUser();
        oauthUser.setPasswordHash(null);
        oauthUser.setGoogleSub("google-sub-123");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("different sign-in");
    }
}
