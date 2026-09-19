package com.tasklean.api.security.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate-limit configuration bound from {@code ratelimit.*}. Each tier is a token bucket that
 * allows {@code capacity} requests and refills its full capacity every {@code refillPeriod}.
 * Defaults suit a single-instance deployment; override any value per environment.
 */
@Configuration
@ConfigurationProperties(prefix = "ratelimit")
@Getter
@Setter
public class RateLimitProperties {

    /** Master switch — set {@code ratelimit.enabled=false} to bypass rate limiting entirely. */
    private boolean enabled = true;

    /** Unauthenticated auth endpoints (login, verify, resend, google) — keyed by IP. */
    private Tier auth = new Tier(10, Duration.ofMinutes(1));

    /** Registration — keyed by IP (spam-account prevention). */
    private Tier register = new Tier(5, Duration.ofHours(1));

    /** Token refresh — keyed by IP. */
    private Tier refresh = new Tier(20, Duration.ofMinutes(1));

    /** Authenticated API calls — keyed by user id. */
    private Tier apiPerUser = new Tier(120, Duration.ofMinutes(1));

    /** Authenticated API calls — also keyed by IP (backstop; must pass alongside the per-user tier). */
    private Tier apiPerIp = new Tier(300, Duration.ofMinutes(1));

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tier {
        private int capacity;
        private Duration refillPeriod;
    }
}
