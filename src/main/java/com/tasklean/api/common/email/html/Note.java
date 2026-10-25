package com.tasklean.api.common.email.html;

/** A quieter paragraph — expiry lines, and the "if this wasn't you" advice. */
public record Note(String text) implements EmailBlock {

    @Override
    public String toHtml() {
        return ("<p class=\"%s\" style=\"margin:0 0 12px;font-family:%s;font-size:14px;"
                + "line-height:22px;color:%s;\">%s</p>")
                .formatted(EmailStyles.CLASS_MUTED, EmailStyles.FONT, EmailStyles.MUTED_FOREGROUND,
                        EmailBlock.escape(text));
    }

    @Override
    public String toText() {
        return text;
    }
}
