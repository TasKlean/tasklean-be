package com.tasklean.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Provides the application-wide {@link Clock} bean, pinned to UTC so all timestamps
 * are consistent regardless of the host's time zone. Inject this wherever the current
 * time is needed instead of calling {@code LocalDateTime.now()} directly.
 */
@Configuration
public class ClockConfig {

    /**
     * The UTC clock injected wherever the current time is needed.
     *
     * @return a system clock fixed to UTC
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
