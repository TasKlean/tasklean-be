package com.tasklean.api.common.email.html;

/** One labelled detail, such as the device and address a sign-in came from. */
public record DataRow(String label, String value) implements EmailBlock {

    @Override
    public String toHtml() {
        return ("""
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" \
                style="margin:0 0 8px;"><tr>\
                <td class="%s" style="font-family:%s;font-size:14px;line-height:22px;color:%s;\
                padding-right:12px;">%s</td>\
                <td class="%s" style="font-family:%s;font-size:14px;line-height:22px;\
                font-weight:600;color:%s;">%s</td>\
                </tr></table>""")
                .formatted(EmailStyles.CLASS_MUTED, EmailStyles.FONT, EmailStyles.MUTED_FOREGROUND,
                        EmailBlock.escape(label),
                        EmailStyles.CLASS_TEXT, EmailStyles.FONT, EmailStyles.FOREGROUND,
                        EmailBlock.escape(value));
    }

    @Override
    public String toText() {
        return label + ": " + value;
    }
}
