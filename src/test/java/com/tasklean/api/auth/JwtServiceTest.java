package com.tasklean.api.auth;

import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-at-least-32-bytes-long!");
        config.setExpiration(86400000L); // 24h
        jwtService = new JwtService(config);
    }

    private User buildTestUser() {
        return User.builder()
                .idUser(42L)
                .uid("usr-abc-123")
                .email("alice@example.com")
                .name("Alice")
                .build();
    }

    @Test
    void generateToken_validUser_returnsNonEmptyToken() {
        String token = jwtService.generateToken(buildTestUser());

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtService.generateToken(buildTestUser());

        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtService.generateToken(buildTestUser());
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtService.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_garbageString_returnsFalse() {
        assertThat(jwtService.validateToken("not.a.jwt")).isFalse();
        assertThat(jwtService.validateToken("")).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-at-least-32-bytes-long!");
        config.setExpiration(-1000L); // already expired
        JwtService expiredJwtService = new JwtService(config);

        String token = expiredJwtService.generateToken(buildTestUser());

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_wrongSecret_returnsFalse() {
        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("different-secret-key-also-at-least-32-bytes!!");
        otherConfig.setExpiration(86400000L);
        JwtService otherService = new JwtService(otherConfig);

        String token = otherService.generateToken(buildTestUser());

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void extractEmail_returnsSubjectFromToken() {
        String token = jwtService.generateToken(buildTestUser());

        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void extractUserId_returnsInternalPkFromToken() {
        String token = jwtService.generateToken(buildTestUser());

        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void extractUid_returnsPublicUidFromToken() {
        String token = jwtService.generateToken(buildTestUser());

        assertThat(jwtService.extractUid(token)).isEqualTo("usr-abc-123");
    }
}
