package com.tasklean.api.domain.device;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Derives a readable {@link DeviceInfo} (form factor, browser, OS, display name) from a raw
 * user-agent string. Deliberately a small set of substring checks covering the common desktop and
 * mobile browsers — enough to label a device in the settings list, not a full UA database.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserAgentParser {

    private static final String UNKNOWN = "Unknown";

    /**
     * Parses a user-agent string into a {@link DeviceInfo}.
     *
     * @param userAgent the raw user-agent string (may be {@code null} or blank)
     * @return the derived device info; a generic "Unknown device" when nothing is recognised
     */
    public static DeviceInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceInfo("unknown", UNKNOWN, UNKNOWN, "Unknown device");
        }

        String ua = userAgent.toLowerCase();
        String os = detectOs(ua);
        String browser = detectBrowser(ua);
        String type = isMobile(ua) ? "mobile" : "desktop";
        return new DeviceInfo(type, browser, os, displayName(browser, os));
    }

    private static String detectOs(String ua) {
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        // iOS devices carry "like Mac OS X", so check them before the macOS match below.
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        return UNKNOWN;
    }

    private static String detectBrowser(String ua) {
        // Order matters: Edge and Opera UAs also contain "chrome"; Chrome's contains "safari".
        if (ua.contains("edg")) return "Edge";
        if (ua.contains("opr") || ua.contains("opera")) return "Opera";
        if (ua.contains("crios") || ua.contains("chrome")) return "Chrome";
        if (ua.contains("firefox") || ua.contains("fxios")) return "Firefox";
        if (ua.contains("safari")) return "Safari";
        return UNKNOWN;
    }

    private static boolean isMobile(String ua) {
        return ua.contains("mobi") || ua.contains("android")
                || ua.contains("iphone") || ua.contains("ipad");
    }

    private static String displayName(String browser, String os) {
        boolean unknownBrowser = UNKNOWN.equals(browser);
        boolean unknownOs = UNKNOWN.equals(os);
        if (unknownBrowser && unknownOs) {
            return "Unknown device";
        }
        return (unknownBrowser ? "Browser" : browser) + " on " + (unknownOs ? "unknown OS" : os);
    }
}
