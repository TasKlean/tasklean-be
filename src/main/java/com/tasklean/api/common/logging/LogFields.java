package com.tasklean.api.common.logging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * MDC (Mapped Diagnostic Context) keys attached to log lines for correlation.
 * In prod these become top-level fields in the ECS JSON output, so logs can be
 * queried by request or user in Kibana/Grafana. Centralised so producers
 * (filters) and any future consumers use the same names.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogFields {

    /** Correlation id for a single HTTP request; echoed back in the X-Request-Id response header. */
    public static final String REQUEST_ID = "requestId";
    /** Authenticated caller's uid; set by JwtAuthenticationFilter once the token is validated. */
    public static final String USER_ID = "userId";
    public static final String METHOD = "method";
    public static final String PATH = "path";
    public static final String STATUS = "status";
    public static final String DURATION_MS = "durationMs";

    /** Incoming header used to adopt a caller-supplied correlation id (e.g. from a gateway) if present. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
}
