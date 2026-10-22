package com.tasklean.api.common.web;

import com.google.common.net.InetAddresses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the end user's IP for the current request, and says whether that value came from a
 * caller we trust. The single source for both consumers: the audit trail and new-sign-in emails
 * (which want who acted) and {@code RateLimitFilter} (which wants a key the caller cannot choose).
 *
 * <p>Browsers never reach the API directly — our frontend BFF calls it server-to-server — so
 * {@code getRemoteAddr()} is Cloudflare's edge, never the user. The BFF therefore forwards the
 * user's IP in {@code X-Device-Ip}. Any caller can send that header, so it is honoured only when
 * {@code X-Internal-Hop} proves the request came from our own frontend.
 */
@Slf4j
@Component
public class ClientIpResolver {

    // Set by our BFF from the incoming CF-Connecting-IP: the browser's address, not the BFF's.
    public static final String DEVICE_IP_HEADER = "X-Device-Ip";
    // Possession of the shared secret is what makes DEVICE_IP_HEADER trustworthy. Named for the hop,
    // not for auth: it says who called us, never that the call is authorized.
    public static final String HOP_SECRET_HEADER = "X-Internal-Hop";
    // Set by Cloudflare from the TCP peer, so unforgeable - but on the BFF hop it is the BFF's own
    // egress address. Only a direct caller's CF-Connecting-IP is the end user.
    public static final String CLOUDFLARE_IP_HEADER = "CF-Connecting-IP";

    // Longest valid textual IPv6 address, and the width of the ip_address column on both device and
    // audit_log - so rejecting anything longer keeps a client-supplied value from failing an insert.
    private static final int MAX_IP_LENGTH = 45;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final int IPV6_BYTES = 16;
    private static final int IPV6_PREFIX_BYTES = 8;

    private final List<byte[]> hopSecrets;

    /**
     * @param configured comma-separated hop secrets from {@code BFF_HOP_SECRETS}; several allow
     *                   rotation, and empty means no caller is ever trusted
     * @throws IllegalStateException if a configured secret is shorter than 32 characters — a
     *                               guessable trust secret must fail at startup, not at first use
     */
    public ClientIpResolver(@Value("${bff.hop-secrets:}") String configured) {
        this.hopSecrets = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(secret -> !secret.isEmpty())
                .peek(ClientIpResolver::requireStrongSecret)
                .map(secret -> secret.getBytes(StandardCharsets.UTF_8))
                .toList();

        if (hopSecrets.isEmpty()) {
            // Expected in dev. In a deployed environment it silently disables per-user throttling,
            // so it is worth a line in the startup log rather than a surprise under load.
            log.warn("No BFF hop secret configured: client-supplied IPs will be ignored");
        }
    }

    /**
     * Resolves the IP to attribute this request to, preferring the BFF's forwarded value when the
     * hop is proven, then Cloudflare's own, then the socket address.
     *
     * @param request the current request
     * @return the resolved IP and whether it came from a trusted hop; the IP is {@code null} only
     *         if the container reports no remote address
     */
    public ResolvedIp resolve(HttpServletRequest request) {
        if (isTrustedHop(request)) {
            String forwarded = validIp(request.getHeader(DEVICE_IP_HEADER));
            if (forwarded != null) {
                return new ResolvedIp(forwarded, true);
            }
            // Our own frontend proved itself but sent no usable IP, so every user it proxies now
            // falls into one bucket below. That is a frontend bug, not a caller we should punish.
            log.warn("Trusted hop sent no valid {} header", DEVICE_IP_HEADER);
        }

        String cloudflare = validIp(request.getHeader(CLOUDFLARE_IP_HEADER));
        if (cloudflare != null) {
            return new ResolvedIp(cloudflare, false);
        }
        return new ResolvedIp(request.getRemoteAddr(), false);
    }

    /**
     * Narrows an IP to the key throttling should count against. A single user is routinely handed a
     * whole IPv6 /64, so counting exact v6 addresses would let them rotate for free.
     *
     * @param ip the resolved IP, or {@code null}
     * @return the /64 prefix for an IPv6 address, or the IP unchanged
     */
    public String throttleKey(String ip) {
        if (ip == null || ip.indexOf(':') < 0 || !InetAddresses.isInetAddress(ip)) {
            return ip;
        }
        byte[] bytes = InetAddresses.forString(ip).getAddress();
        // An IPv4-mapped literal (::ffff:1.2.3.4) parses to 4 bytes and has no /64 to take.
        if (bytes.length != IPV6_BYTES) {
            return ip;
        }
        byte[] prefix = new byte[IPV6_BYTES];
        System.arraycopy(bytes, 0, prefix, 0, IPV6_PREFIX_BYTES);
        try {
            return InetAddresses.toAddrString(InetAddress.getByAddress(prefix)) + "/64";
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    private static void requireStrongSecret(String secret) {
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "BFF_HOP_SECRETS entries must be at least " + MIN_SECRET_LENGTH + " characters");
        }
    }

    private boolean isTrustedHop(HttpServletRequest request) {
        String provided = request.getHeader(HOP_SECRET_HEADER);
        if (provided == null) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        for (byte[] secret : hopSecrets) {
            if (MessageDigest.isEqual(providedBytes, secret)) {
                return true;
            }
        }
        // Either a rotation that went wrong or someone probing. Never log the value itself.
        log.warn("Rejected {} header: no configured secret matches", HOP_SECRET_HEADER);
        return false;
    }

    private String validIp(String value) {
        if (value == null || value.length() > MAX_IP_LENGTH || !InetAddresses.isInetAddress(value)) {
            return null;
        }
        return value;
    }

    /**
     * A resolved client IP and the trust behind it. {@code trustedHop} is {@code false} whenever the
     * value is infrastructure's rather than the end user's, so it is not safe as a throttling key.
     */
    public record ResolvedIp(String ip, boolean trustedHop) {
    }
}
