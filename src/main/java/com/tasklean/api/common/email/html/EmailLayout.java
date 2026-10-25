package com.tasklean.api.common.email.html;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps a template's blocks in the message shell — the branded header band, the card the content
 * sits in, and the footer — and renders the same blocks as plain text for the fallback part.
 *
 * <p>Tables and inline styles throughout: Outlook on Windows renders with Word's engine, which has
 * no flexbox, no grid and no CSS variables. The dark scheme is the one thing that cannot be inline,
 * since a media query can only reach a class.
 */
public final class EmailLayout {

    /** The content id the logo is attached under, written here and attached by the mail sender. */
    public static final String LOGO_CID = "logo";

    private EmailLayout() {
    }

    /**
     * Renders the blocks as a complete HTML document.
     *
     * @param blocks the email's content, in order
     * @return the document, ready to be sent as the HTML part
     */
    public static String renderHtml(List<EmailBlock> blocks) {
        String body = blocks.stream().map(EmailBlock::toHtml).collect(Collectors.joining("\n"));
        return head() + header() + card(body) + footer();
    }

    /**
     * Renders the same blocks as plain text, so the two parts of the message can never drift: they
     * are two projections of one declaration.
     *
     * @param blocks the email's content, in order
     * @return the text part
     */
    public static String renderText(List<EmailBlock> blocks) {
        return blocks.stream().map(EmailBlock::toText).collect(Collectors.joining("\n\n"));
    }

    private static String head() {
        // color-scheme tells a client the message is drawn for both schemes; the media query is
        // honoured by Apple Mail and Outlook, while Gmail force-inverts and ignores it either way.
        return ("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light dark">
                <meta name="supported-color-schemes" content="light dark">
                <style>
                @media (prefers-color-scheme: dark) {
                  .%s { background-color:%s !important; }
                  .%s { background-color:%s !important; border-color:%s !important; }
                  .%s { color:%s !important; }
                  .%s { color:%s !important; }
                  .%s { color:%s !important; }
                  .%s { color:%s !important; }
                  .%s { background-color:%s !important; border-color:%s !important; color:%s !important; }
                  .%s { border-color:%s !important; }
                }
                </style>
                </head>
                <body style="margin:0;padding:0;background-color:%s;">
                """)
                .formatted(EmailStyles.CLASS_PAGE, EmailStyles.DARK_BACKGROUND,
                        EmailStyles.CLASS_CARD, EmailStyles.DARK_CARD, EmailStyles.DARK_BORDER,
                        EmailStyles.CLASS_TEXT, EmailStyles.DARK_FOREGROUND,
                        EmailStyles.CLASS_HEADING, EmailStyles.DARK_BRAND_STRONG,
                        EmailStyles.CLASS_MUTED, EmailStyles.DARK_MUTED_FOREGROUND,
                        EmailStyles.CLASS_LINK, EmailStyles.DARK_LINK,
                        EmailStyles.CLASS_CODE, EmailStyles.DARK_BACKGROUND,
                        EmailStyles.DARK_BORDER, EmailStyles.DARK_FOREGROUND,
                        EmailStyles.CLASS_DIVIDER, EmailStyles.DARK_BORDER,
                        EmailStyles.BACKGROUND);
    }

    private static String header() {
        // The band stays slate in both schemes, which is why one logo covers both: the light-blue
        // mark reads on it either way, and slate is the dark scheme's own accent.
        return ("""
                <table role="presentation" class="%s" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:%s;">
                <tr><td align="center" style="padding:28px 12px;">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%%;max-width:600px;">
                <tr><td bgcolor="%s" style="background-color:%s;border-radius:12px 12px 0 0;padding:20px 28px;">
                <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
                <td style="padding-right:10px;"><img src="cid:%s" width="36" height="36" alt="TasKlean" style="display:block;border:0;"></td>
                <td style="font-family:%s;font-size:20px;font-weight:600;letter-spacing:-0.01em;color:%s;">TasKlean</td>
                </tr></table>
                </td></tr>
                """)
                .formatted(EmailStyles.CLASS_PAGE, EmailStyles.BACKGROUND,
                        EmailStyles.BRAND, EmailStyles.BRAND,
                        LOGO_CID, EmailStyles.FONT, EmailStyles.ON_BRAND);
    }

    private static String card(String body) {
        return ("""
                <tr><td class="%s" style="background-color:%s;border:1px solid %s;border-top:none;border-radius:0 0 12px 12px;padding:28px;">
                %s
                </td></tr>
                """)
                .formatted(EmailStyles.CLASS_CARD, EmailStyles.CARD, EmailStyles.BORDER, body);
    }

    private static String footer() {
        return ("""
                <tr><td class="%s" align="center" style="padding:18px 28px 0;font-family:%s;font-size:12px;line-height:18px;color:%s;">
                TasKlean &middot; you are getting this because of activity on your account.
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """)
                .formatted(EmailStyles.CLASS_MUTED, EmailStyles.FONT, EmailStyles.MUTED_FOREGROUND);
    }
}
