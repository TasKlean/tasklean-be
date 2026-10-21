package com.tasklean.api.domain.usersettings.dto;

import com.tasklean.api.domain.usersettings.AppLocale;
import com.tasklean.api.domain.usersettings.Theme;
import com.tasklean.api.domain.usersettings.UserSettings;

/**
 * The caller's UI settings.
 *
 * @param theme  the colour-scheme preference
 * @param locale the UI language (lowercase the name for the BCP-47 tag)
 */
public record UserSettingsResponse(Theme theme, AppLocale locale) {

    public static UserSettingsResponse from(UserSettings settings) {
        return new UserSettingsResponse(settings.getTheme(), settings.getLocale());
    }

    /** What a user gets back before they have ever saved settings (nothing is persisted). */
    public static UserSettingsResponse defaults() {
        return new UserSettingsResponse(UserSettings.DEFAULT_THEME, UserSettings.DEFAULT_LOCALE);
    }
}
