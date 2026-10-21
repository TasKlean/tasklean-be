package com.tasklean.api.domain.device;

/**
 * Human-readable device details derived from a user-agent string: a coarse form factor, the
 * browser and OS names, and a friendly display name (e.g. "Chrome on Windows").
 */
public record DeviceInfo(String type, String browser, String os, String displayName) {
}
