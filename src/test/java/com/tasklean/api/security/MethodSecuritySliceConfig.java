package com.tasklean.api.security;

import com.tasklean.api.security.ratelimit.BucketRegistry;
import com.tasklean.api.security.ratelimit.CaffeineBucketRegistry;
import com.tasklean.api.security.ratelimit.RateLimitProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared test config for {@code @WebMvcTest} security slices: enables method security with the
 * production role hierarchy, behind a permissive filter chain so only {@code @PreAuthorize}
 * decides. Import it alongside the security beans a controller's expressions reference. Tests
 * inject the caller per-request via {@code SecurityMockMvcRequestPostProcessors.authentication(...)}.
 */
@TestConfiguration
@EnableMethodSecurity
public class MethodSecuritySliceConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMIN").implies("ADMIN")
                .role("ADMIN").implies("USER")
                .build();
    }

    // The @Component RateLimitFilter is pulled into every @WebMvcTest slice; give it its deps
    // (generous default tiers, so the one request per test never trips a limit).
    @Bean
    RateLimitProperties rateLimitProperties() {
        return new RateLimitProperties();
    }

    @Bean
    BucketRegistry bucketRegistry() {
        return new CaffeineBucketRegistry();
    }
}
