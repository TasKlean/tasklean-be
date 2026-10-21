package com.tasklean.api.domain.usersettings;

/**
 * The UI languages the app supports, as ISO 639-1 codes. Named {@code AppLocale} so it never
 * collides with {@link java.util.Locale}. Stored as the enum name in {@code user_settings.locale};
 * clients lowercase it to get the BCP-47 tag ({@code SL} → {@code sl}).
 */
public enum AppLocale {

    EN,
    DE,
    SL
}
