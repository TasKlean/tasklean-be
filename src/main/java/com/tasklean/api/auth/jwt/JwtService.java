package com.tasklean.api.auth.jwt;

import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles JWT token creation, validation, and claim extraction.
 * Tokens use HMAC-SHA signing and carry the user's email (subject), internal ID, UID,
 * and platform role.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    /**
     * Creates a signed JWT containing the user's email, internal ID, public UID, and platform role.
     *
     * @param user the user to issue the token for
     * @return the compact signed JWT
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtConfig.getExpiration());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getIdUser())
                .claim("uid", user.getUid())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Checks whether a token's signature is valid and it has not expired.
     *
     * @param token the JWT to validate
     * @return {@code true} if the token is valid and unexpired, {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extracts the user's email (JWT subject) from a valid token.
     *
     * @param token the JWT
     * @return the email claim
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extracts the internal user ID claim from a valid token.
     *
     * @param token the JWT
     * @return the internal user ID
     */
    public Long extractUserId(String token) {
        return extractClaims(token).get("userId", Long.class);
    }

    /**
     * Extracts the public UID claim from a valid token.
     *
     * @param token the JWT
     * @return the public UID
     */
    public String extractUid(String token) {
        return extractClaims(token).get("uid", String.class);
    }

    /**
     * Extracts the platform role claim from a valid token.
     *
     * @param token the JWT
     * @return the user's platform role; defaults to {@link UserRole#USER} if the claim is empty
     */
    public UserRole extractRole(String token) {
        String role = extractClaims(token).get("role", String.class);
        return role != null ? UserRole.valueOf(role) : UserRole.USER;
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Secret must be >= 32 bytes (256 bits) for HMAC-SHA — enforced by jjwt at runtime
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
