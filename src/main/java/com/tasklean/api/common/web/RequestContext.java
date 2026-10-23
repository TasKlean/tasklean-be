package com.tasklean.api.common.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads device/session metadata off the current HTTP request: the client-generated device id,
 * the end-user's user-agent, and the client IP. Keeps servlet plumbing out of the services that
 * need this when issuing a sign-in session.
 */
@Component
@RequiredArgsConstructor
public class RequestContext {

    private final ClientIpResolver clientIpResolver;

    // Set by the frontend BFF: a stable id it keeps per browser/device, forwarded on auth calls.
    private static final String DEVICE_ID_HEADER = "X-Device-Id";
    // Both match their columns on device, so a client cannot fail an insert with an oversized
    // header. The frontend sends a 36-character UUID and a browser user-agent, far inside these.
    private static final int MAX_DEVICE_ID_LENGTH = 255;
    private static final int MAX_USER_AGENT_LENGTH = 512;
    // The BFF forwards the real browser user-agent here, since the standard User-Agent header on a
    // server-to-server call is the BFF's own. Falls back to User-Agent when absent.
    private static final String DEVICE_USER_AGENT_HEADER = "X-Device-User-Agent";

    /**
     * Returns the stable client-generated device id from the request, if present.
     *
     * @return the device id, or {@code null} if the header is absent, longer than the column, or
     *         no request is bound
     */
    public String deviceId() {
        String deviceId = header(DEVICE_ID_HEADER);
        // Rejected rather than truncated, unlike the user-agent: this is an identity key, and
        // clipping it would fold two distinct devices onto one row.
        return deviceId != null && deviceId.length() > MAX_DEVICE_ID_LENGTH ? null : deviceId;
    }

    /**
     * Returns the end-user's user-agent, preferring the forwarded header over the transport one.
     *
     * @return the user-agent string, or {@code null} if neither header is present
     */
    public String userAgent() {
        String forwarded = header(DEVICE_USER_AGENT_HEADER);
        return truncate(forwarded != null ? forwarded : header("User-Agent"));
    }

    /**
     * Returns the best address available for the current request, whatever vouched for it — for
     * recording a value where none exists yet. Use {@link #trustedClientIp()} to <em>overwrite</em>
     * a stored address, and {@link ClientIpResolver#resolve} where the trust flag itself matters,
     * as rate limiting needs it.
     *
     * @return the client IP, or {@code null} if no request is bound to the current thread
     */
    public String clientIp() {
        ClientIpResolver.ResolvedIp resolved = resolved();
        return resolved != null ? resolved.ip() : null;
    }

    /**
     * Returns the end user's IP only when the caller proved it is ours to believe — for writes that
     * would otherwise replace a real location with infrastructure's. An untrusted request resolves
     * to the BFF's own egress address or a Cloudflare edge, neither of which is where the user is.
     *
     * @return the client IP if a trusted hop vouched for it, otherwise {@code null}
     */
    public String trustedClientIp() {
        ClientIpResolver.ResolvedIp resolved = resolved();
        return resolved != null && resolved.trustedHop() ? resolved.ip() : null;
    }

    private ClientIpResolver.ResolvedIp resolved() {
        HttpServletRequest request = currentRequest();
        return request != null ? clientIpResolver.resolve(request) : null;
    }

    // Truncated rather than rejected: the browser and OS tokens sit at the front, so a clipped
    // value still names the device, where dropping it entirely would not.
    private String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }

    private String header(String name) {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader(name) : null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
