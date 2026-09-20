package com.tasklean.api.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Builds the {@link GoogleIdTokenVerifier} used to validate "Sign in with Google" ID tokens.
 * The verifier checks the token's signature (against Google's cached JWKS), issuer, expiry, and
 * audience — the audience being our OAuth client id(s). Add mobile client ids to the audience
 * list here when native apps are introduced.
 */
@Configuration
public class GoogleOAuthConfig {

    /**
     * Creates the shared, thread-safe Google ID-token verifier pinned to our client id.
     *
     * @param clientId the Google OAuth web client id (from {@code GOOGLE_CLIENT_ID}); the accepted token audience
     * @return a configured {@link GoogleIdTokenVerifier}
     */
    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${google.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }
}
