package com.tasklean.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the {@code jwt.*} properties: the signing secret, the access-token expiration,
 * and the refresh-token expiration (both in milliseconds).
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    private String secret;
    private long expiration;
    private long refreshExpiration;
}
