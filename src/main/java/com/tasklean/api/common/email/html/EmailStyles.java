package com.tasklean.api.common.email.html;

/**
 * The palette and type the emails are drawn with, mirroring the frontend's design tokens
 * (`globals.css`). Hex and px literals rather than CSS variables or rem: Outlook on Windows renders
 * with Word's engine, which supports neither.
 */
final class EmailStyles {

    private EmailStyles() {
    }

    // Webfonts do not load in most mail clients, so Plus Jakarta Sans is only the first preference
    // and the stack degrades to whatever the reader's system provides.
    static final String FONT = "'Plus Jakarta Sans',-apple-system,BlinkMacSystemFont,'Segoe UI',"
            + "Roboto,Helvetica,Arial,sans-serif";
    static final String MONO = "'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace";

    // Light scheme — the frontend's :root tokens.
    static final String BACKGROUND = "#f9faf2";
    static final String CARD = "#ffffff";
    static final String FOREGROUND = "#1a1c18";
    static final String MUTED_FOREGROUND = "#42474d";
    static final String BRAND = "#355872";
    static final String BRAND_STRONG = "#1c415a";
    static final String LINK = "#316384";
    static final String BORDER = "#c2c7cd";
    static final String ON_BRAND = "#ffffff";

    // Dark scheme — the frontend's @variant dark block. Applied through the class names below
    // rather than per element, since a media query can only reach a class.
    static final String DARK_BACKGROUND = "#111410";
    static final String DARK_CARD = "#1a1c18";
    static final String DARK_FOREGROUND = "#f0f1e9";
    static final String DARK_MUTED_FOREGROUND = "#c2c7cd";
    static final String DARK_BRAND_STRONG = "#a7cbe9";
    static final String DARK_LINK = "#9cccf2";
    static final String DARK_BORDER = "#42474d";

    // The contract between a block and the layout's <style> block: a block tags itself, the layout
    // decides what the tag means in dark mode.
    static final String CLASS_PAGE = "tk-page";
    static final String CLASS_CARD = "tk-card";
    static final String CLASS_TEXT = "tk-text";
    static final String CLASS_HEADING = "tk-heading";
    static final String CLASS_MUTED = "tk-muted";
    static final String CLASS_LINK = "tk-link";
    static final String CLASS_CODE = "tk-code";
    static final String CLASS_DIVIDER = "tk-divider";
}
