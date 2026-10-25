package com.tasklean.api.common.email.html;

/** The email's title line. */
public record Heading(String text) implements EmailBlock {

    @Override
    public String toHtml() {
        return ("<h1 class=\"%s\" style=\"margin:0 0 14px;font-family:%s;font-size:24px;"
                + "line-height:32px;font-weight:600;color:%s;\">%s</h1>")
                .formatted(EmailStyles.CLASS_HEADING, EmailStyles.FONT, EmailStyles.BRAND_STRONG,
                        EmailBlock.escape(text));
    }

    @Override
    public String toText() {
        return text;
    }
}
