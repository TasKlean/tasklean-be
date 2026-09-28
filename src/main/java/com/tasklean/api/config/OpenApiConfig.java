package com.tasklean.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Describes the API for the generated OpenAPI spec: title and version, the consumer-facing
 * conventions a client needs to know, and the bearer-JWT security scheme that every endpoint
 * requires unless it opts out (see {@code @SecurityRequirements} on the public auth endpoints).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    private final Optional<BuildProperties> buildProperties;

    public OpenApiConfig(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    /**
     * Builds the OpenAPI document metadata and security scheme. springdoc derives paths, schemas,
     * and validation constraints from the running context, so only what it cannot infer is set here.
     *
     * @return the OpenAPI definition springdoc merges its generated paths into
     */
    @Bean
    public OpenAPI taskleanOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TasKlean API")
                        .version(version())
                        .description("""
                                Household task management API.

                                Conventions callers need:
                                - Every response is the envelope `{ success, message, data }`.
                                - `user`, `group` and `task` are addressed by opaque `uid`; \
                                every other resource by numeric `id`.
                                - Timestamps are UTC with no zone suffix — parse as UTC, convert for display.
                                - Actors are derived from the token; never send a user or member id \
                                to identify the caller.
                                - Errors: 400 validation, 401 unauthenticated, 403 forbidden, 404 not found, \
                                409 duplicate or violated business rule, 429 rate limited (see `Retry-After`).
                                - Role requirements are stated per operation and are not modelled in this spec.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from /api/auth/login, /api/auth/verify-email, "
                                        + "/api/auth/google or /api/auth/refresh.")))
                // Applied globally: public endpoints opt out rather than every protected one opting in,
                // so a new endpoint is documented as protected by default.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private String version() {
        return buildProperties.map(BuildProperties::getVersion).orElse("dev");
    }
}
