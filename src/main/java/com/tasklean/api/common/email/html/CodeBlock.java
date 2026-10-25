package com.tasklean.api.common.email.html;

/** A code the reader is expected to read off and type, set large and letter-spaced. */
public record CodeBlock(String code) implements EmailBlock {

    @Override
    public String toHtml() {
        return ("""
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" \
                style="margin:4px 0 18px;"><tr>\
                <td class="%s" align="center" style="background-color:%s;border:1px solid %s;\
                border-radius:8px;padding:14px 26px;font-family:%s;font-size:28px;font-weight:600;\
                letter-spacing:6px;color:%s;">%s</td>\
                </tr></table>""")
                .formatted(EmailStyles.CLASS_CODE, EmailStyles.BACKGROUND, EmailStyles.BORDER,
                        EmailStyles.MONO, EmailStyles.FOREGROUND, EmailBlock.escape(code));
    }

    @Override
    public String toText() {
        return code;
    }
}
