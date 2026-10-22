package com.tasklean.api.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static final String SECRET = "dGVzdC1ob3Atc2VjcmV0LTMyLWJ5dGVzLWxvbmc9";
    private static final String ROTATED_SECRET = "cm90YXRlZC1ob3Atc2VjcmV0LTMyLWJ5dGVzPT0=";

    private final ClientIpResolver resolver = new ClientIpResolver(SECRET);

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.70.240.123");
        return request;
    }

    // --- trusted hop ---

    @Test
    void resolve_trustedHopWithDeviceIp_returnsForwardedIp() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");
        request.addHeader(ClientIpResolver.CLOUDFLARE_IP_HEADER, "198.51.100.4");

        ClientIpResolver.ResolvedIp resolved = resolver.resolve(request);

        assertThat(resolved.ip()).isEqualTo("203.0.113.7");
        assertThat(resolved.trustedHop()).isTrue();
    }

    @Test
    void resolve_rotatedSecret_isStillTrusted() {
        ClientIpResolver rotating = new ClientIpResolver(SECRET + "," + ROTATED_SECRET);
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, ROTATED_SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");

        assertThat(rotating.resolve(request).trustedHop()).isTrue();
    }

    @Test
    void resolve_trustedHopWithIpv6DeviceIp_returnsIt() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "2001:db8::1");

        assertThat(resolver.resolve(request).ip()).isEqualTo("2001:db8::1");
    }

    // --- untrusted callers cannot assert an IP ---

    @Test
    void resolve_deviceIpWithoutHopSecret_ignoresForgedHeader() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");
        request.addHeader(ClientIpResolver.CLOUDFLARE_IP_HEADER, "198.51.100.4");

        ClientIpResolver.ResolvedIp resolved = resolver.resolve(request);

        assertThat(resolved.ip()).isEqualTo("198.51.100.4");
        assertThat(resolved.trustedHop()).isFalse();
    }

    @Test
    void resolve_deviceIpWithWrongHopSecret_ignoresForgedHeader() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, ROTATED_SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");

        assertThat(resolver.resolve(request).ip()).isEqualTo("172.70.240.123");
    }

    @Test
    void resolve_noSecretConfigured_trustsNobody() {
        ClientIpResolver untrusting = new ClientIpResolver("");
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7");

        assertThat(untrusting.resolve(request).trustedHop()).isFalse();
    }

    // --- validation ---

    @Test
    void resolve_deviceIpNotAnIpLiteral_fallsThrough() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "attacker.example.com");
        request.addHeader(ClientIpResolver.CLOUDFLARE_IP_HEADER, "198.51.100.4");

        ClientIpResolver.ResolvedIp resolved = resolver.resolve(request);

        assertThat(resolved.ip()).isEqualTo("198.51.100.4");
        assertThat(resolved.trustedHop()).isFalse();
    }

    @Test
    void resolve_deviceIpIsAList_fallsThrough() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "203.0.113.7, 10.0.0.1");

        assertThat(resolver.resolve(request).ip()).isEqualTo("172.70.240.123");
    }

    @Test
    void resolve_deviceIpLongerThanColumn_fallsThrough() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.HOP_SECRET_HEADER, SECRET);
        // 49 characters: a scoped literal is the one shape that can outrun the 45-char column.
        request.addHeader(ClientIpResolver.DEVICE_IP_HEADER, "2001:0db8:0000:0000:0000:0000:0000:0001%ethernet0");

        assertThat(resolver.resolve(request).ip()).isEqualTo("172.70.240.123");
    }

    @Test
    void resolve_cloudflareHeaderNotAnIpLiteral_fallsBackToSocket() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpResolver.CLOUDFLARE_IP_HEADER, "not-an-ip");

        assertThat(resolver.resolve(request).ip()).isEqualTo("172.70.240.123");
    }

    @Test
    void resolve_noHeaders_returnsSocketAddress() {
        assertThat(resolver.resolve(request()).ip()).isEqualTo("172.70.240.123");
    }

    // --- startup validation ---

    @Test
    void construct_guessableSecret_failsFast() {
        assertThatThrownBy(() -> new ClientIpResolver("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    // --- throttle key ---

    @Test
    void throttleKey_ipv6_narrowsToPrefix() {
        assertThat(resolver.throttleKey("2001:db8:1:2:3:4:5:6")).isEqualTo("2001:db8:1:2::/64");
    }

    @Test
    void throttleKey_ipv6AddressesInOnePrefix_shareOneKey() {
        assertThat(resolver.throttleKey("2001:db8::1"))
                .isEqualTo(resolver.throttleKey("2001:db8::ffff"));
    }

    @Test
    void throttleKey_ipv4_isUnchanged() {
        assertThat(resolver.throttleKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void throttleKey_ipv4MappedIpv6_isUnchanged() {
        assertThat(resolver.throttleKey("::ffff:203.0.113.7")).isEqualTo("::ffff:203.0.113.7");
    }

    @Test
    void throttleKey_null_isNull() {
        assertThat(resolver.throttleKey(null)).isNull();
    }
}
