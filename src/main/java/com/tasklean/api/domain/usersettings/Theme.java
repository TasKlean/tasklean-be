package com.tasklean.api.domain.usersettings;

/**
 * A user's colour-scheme preference for the UI. {@code SYSTEM} follows the device setting.
 * Stored as the enum name in {@code user_settings.theme}.
 */
public enum Theme {

    LIGHT,
    DARK,

    // Default: follow whatever the operating system / browser reports.
    SYSTEM
}
