package com.tasklean.api.common.email.html;

/** A body paragraph. */
public record Paragraph(String text) implements EmailBlock {

    @Override
    public String toHtml() {
        return ("<p class=\"%s\" style=\"margin:0 0 16px;font-family:%s;font-size:16px;"
                + "line-height:26px;color:%s;\">%s</p>")
                .formatted(EmailStyles.CLASS_TEXT, EmailStyles.FONT, EmailStyles.FOREGROUND,
                        EmailBlock.escape(text));
    }

    @Override
    public String toText() {
        return text;
    }
}
