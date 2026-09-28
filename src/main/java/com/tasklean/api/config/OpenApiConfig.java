package com.tasklean.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
// Swagger's response model, not com.tasklean.api.common.ApiResponse — the two share a simple name,
// so any file needing both must qualify one of them. This one only uses Swagger's.
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String BAD_REQUEST = "BadRequest";
    private static final String UNAUTHORIZED = "Unauthorized";
    private static final String FORBIDDEN = "Forbidden";
    private static final String TOO_MANY_REQUESTS = "TooManyRequests";
    private static final String SERVER_ERROR = "InternalServerError";

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

    /**
     * Adds the error responses that apply across the API, so the shared envelope is documented once
     * rather than annotated on every operation.
     *
     * <p>Only genuinely applicable codes are attached. {@code 404} and {@code 409} are deliberately
     * left out: they are operation-specific, and any rule general enough to apply automatically
     * would be wrong somewhere — {@code POST /api/groups/join} returns 404 with no path variable,
     * while list endpoints with a path variable never do.
     *
     * @return a customizer that registers the shared error schema and attaches the common responses
     */
    @Bean
    public OpenApiCustomizer commonErrorResponsesCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            components.addSchemas(ERROR_SCHEMA, errorSchema());
            components.addResponses(BAD_REQUEST, errorResponse(
                    "Validation failure, malformed body, or a bad path/query parameter"));
            components.addResponses(UNAUTHORIZED, errorResponse(
                    "No valid access token — or, on the public auth endpoints, rejected credentials"));
            components.addResponses(FORBIDDEN, errorResponse(
                    "Authenticated, but lacking the platform or group role this operation requires"));
            components.addResponses(TOO_MANY_REQUESTS, errorResponse(
                    "Rate limit exceeded — see the Retry-After header"));
            components.addResponses(SERVER_ERROR, errorResponse("Unexpected server error"));

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();
                // Anything with input can fail validation or be sent malformed.
                if (operation.getRequestBody() != null || operation.getParameters() != null) {
                    responses.addApiResponse("400", responseRef(BAD_REQUEST));
                }
                responses.addApiResponse("401", responseRef(UNAUTHORIZED));
                // An empty security list is the public-endpoint override on AuthController: with no
                // authorization check to fail, those operations cannot produce a 403.
                if (operation.getSecurity() == null || !operation.getSecurity().isEmpty()) {
                    responses.addApiResponse("403", responseRef(FORBIDDEN));
                }
                responses.addApiResponse("429", responseRef(TOO_MANY_REQUESTS));
                responses.addApiResponse("500", responseRef(SERVER_ERROR));
            }));
        };
    }

    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .description("The standard envelope, as returned for a failed request.")
                .addProperty("success", new BooleanSchema()._default(false))
                .addProperty("message", new StringSchema().description("Human-readable error message"))
                .addProperty("data", new ObjectSchema().nullable(true)
                        .description("Always null on an error"));
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }

    private static ApiResponse responseRef(String name) {
        return new ApiResponse().$ref("#/components/responses/" + name);
    }

    private String version() {
        return buildProperties.map(BuildProperties::getVersion).orElse("dev");
    }
}
