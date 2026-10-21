package com.tasklean.api.domain.device;

/**
 * Result of resolving the device for a sign-in: the device the session runs on (may be
 * {@code null} when the login carried no device id) and whether it was first seen on this login.
 */
public record DeviceLoginOutcome(Device device, boolean isNew) {
}
