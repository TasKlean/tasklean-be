package com.tasklean.api.domain.usersettings.dto;

import com.tasklean.api.domain.usersettings.AppLocale;
import com.tasklean.api.domain.usersettings.Theme;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/** Request body for replacing the caller's settings — a full PUT, so both fields are required. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettingsRequest {

    @NotNull(message = "Theme is required")
    private Theme theme;

    @NotNull(message = "Locale is required")
    private AppLocale locale;
}
