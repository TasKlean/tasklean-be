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
    // The BFF forwards the real browser user-agent here, since the standard User-Agent header on a
    // server-to-server call is the BFF's own. Falls back to User-Agent when absent.
    private static final String DEVICE_USER_AGENT_HEADER = "X-Device-User-Agent";

    /**
     * Returns the stable client-generated device id from the request, if present.
     *
     * @return the device id, or {@code null} if the header is absent or no request is bound
     */
    public String deviceId() {
        return header(DEVICE_ID_HEADER);
    }

    /**
     * Returns the end-user's user-agent, preferring the forwarded header over the transport one.
     *
     * @return the user-agent string, or {@code null} if neither header is present
     */
    public String userAgent() {
        String forwarded = header(DEVICE_USER_AGENT_HEADER);
        return forwarded != null ? forwarded : header("User-Agent");
    }

    /**
     * Returns the end user's IP for the current request, for attribution — the audit trail and
     * new-sign-in emails. Throttling must call {@link ClientIpResolver#resolve} directly instead,
     * because the trust flag discarded here is what keeps a forged IP out of a rate-limit key.
     *
     * @return the client IP, or {@code null} if no request is bound to the current thread
     */
    public String clientIp() {
        HttpServletRequest request = currentRequest();
        return request != null ? clientIpResolver.resolve(request).ip() : null;
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
