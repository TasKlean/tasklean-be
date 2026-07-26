package com.tasklean.api.auth.jwt;

import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.user.User;
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
 * Tokens use HMAC-SHA signing and carry the user's email (subject), internal ID, and UID.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    /** Creates a signed JWT containing the user's email, internal ID, and public UID. */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtConfig.getExpiration());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getIdUser())
                .claim("uid", user.getUid())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    /** Returns true if the token signature is valid and not expired. */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractClaims(token).get("userId", Long.class);
    }

    public String extractUid(String token) {
        return extractClaims(token).get("uid", String.class);
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
