package com.tasklean.api.common.email.html;

/**
 * A call-to-action button, always followed by the same URL written out in full: a client that
 * strips the button, or a reader on a device that will not open it, still has the link.
 */
public record ButtonLink(String label, String url) implements EmailBlock {

    @Override
    public String toHtml() {
        String href = EmailBlock.escape(url);
        // A table around the anchor, with bgcolor beside the inline style: Outlook on Windows
        // ignores padding on an inline element and drops background-color.
        return ("""
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" \
                style="margin:4px 0 20px;"><tr>\
                <td align="center" bgcolor="%s" style="border-radius:8px;">\
                <a href="%s" style="display:inline-block;padding:13px 30px;font-family:%s;\
                font-size:16px;font-weight:600;line-height:20px;color:%s;text-decoration:none;\
                border-radius:8px;">%s</a>\
                </td></tr></table>
                <p class="%s" style="margin:0 0 6px;font-family:%s;font-size:14px;line-height:22px;\
                color:%s;">If the button doesn't work, copy this link into your browser:</p>
                <p style="margin:0 0 16px;"><a href="%s" class="%s" style="font-family:%s;\
                font-size:14px;line-height:22px;color:%s;word-break:break-all;">%s</a></p>""")
                .formatted(EmailStyles.BRAND, href, EmailStyles.FONT, EmailStyles.ON_BRAND,
                        EmailBlock.escape(label),
                        EmailStyles.CLASS_MUTED, EmailStyles.FONT, EmailStyles.MUTED_FOREGROUND,
                        href, EmailStyles.CLASS_LINK, EmailStyles.FONT, EmailStyles.LINK, href);
    }

    @Override
    public String toText() {
        return label + ":\n" + url;
    }
}
