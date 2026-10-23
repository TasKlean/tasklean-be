package com.tasklean.api.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    private static final String SECRET = "dGVzdC1ob3Atc2VjcmV0LTMyLWJ5dGVzLWxvbmc9";

    private final RequestContext requestContext = new RequestContext(new ClientIpResolver(SECRET));

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.70.240.123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    // --- clientIp ---

    @Test
    void clientIp_trustedHop_returnsForwardedUserIp() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");

        assertThat(requestContext.clientIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void clientIp_untrustedCaller_ignoresForwardedHeader() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");

        assertThat(requestContext.clientIp()).isEqualTo("172.70.240.123");
    }

    @Test
    void clientIp_noBoundRequest_returnsNull() {
        assertThat(requestContext.clientIp()).isNull();
    }

    // --- trustedClientIp ---

    @Test
    void trustedClientIp_trustedHop_returnsForwardedUserIp() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");

        assertThat(requestContext.trustedClientIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void trustedClientIp_untrustedCaller_returnsNull() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader(ClientIpResolver.CLOUDFLARE_IP_HEADER, "198.51.100.4");

        // Resolvable, but it is our frontend's egress address rather than the user's.
        assertThat(requestContext.trustedClientIp()).isNull();
    }

    @Test
    void trustedClientIp_noBoundRequest_returnsNull() {
        assertThat(requestContext.trustedClientIp()).isNull();
    }

    // --- userAgent ---

    @Test
    void userAgent_forwardedHeaderPresent_prefersItOverTransport() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Device-User-Agent", "Mozilla/5.0 (iPhone)");
        request.addHeader("User-Agent", "node-fetch/1.0");

        assertThat(requestContext.userAgent()).isEqualTo("Mozilla/5.0 (iPhone)");
    }

    @Test
    void userAgent_noForwardedHeader_fallsBackToTransport() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("User-Agent", "node-fetch/1.0");

        assertThat(requestContext.userAgent()).isEqualTo("node-fetch/1.0");
    }

    @Test
    void userAgent_longerThanColumn_isTruncated() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Device-User-Agent", "Mozilla/5.0 " + "x".repeat(600));

        String userAgent = requestContext.userAgent();

        assertThat(userAgent).hasSize(512).startsWith("Mozilla/5.0 ");
    }

    // --- deviceId ---

    @Test
    void deviceId_headerPresent_returnsIt() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Device-Id", "device-1");

        assertThat(requestContext.deviceId()).isEqualTo("device-1");
    }

    @Test
    void deviceId_longerThanColumn_returnsNull() {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Device-Id", "d".repeat(256));

        // Rejected, not clipped: a truncated identity key would merge distinct devices.
        assertThat(requestContext.deviceId()).isNull();
    }

    @Test
    void deviceId_headerAbsent_returnsNull() {
        bindRequest();

        assertThat(requestContext.deviceId()).isNull();
    }
}
