package com.tasklean.api.common.email.html;

import org.springframework.web.util.HtmlUtils;

/**
 * One piece of an email's content, able to render itself as HTML and as plain text. A template
 * declares an email as a list of these, so the two parts of the message are two projections of one
 * declaration rather than two bodies that have to be kept in step by hand.
 */
public sealed interface EmailBlock
        permits Heading, Paragraph, Note, ButtonLink, CodeBlock, DataRow {

    /**
     * @return this block as HTML, with every interpolated value escaped
     */
    String toHtml();

    /**
     * @return this block as plain text, for the message's fallback part
     */
    String toText();

    /**
     * Escapes a value for HTML. UTF-8 is passed so that accented characters stay themselves instead
     * of becoming numeric entities - the document already declares that charset.
     *
     * @param value the raw value, possibly client-supplied (a device name, for instance)
     * @return the value, safe to interpolate into markup or an attribute
     */
    static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    /**
     * @param text the heading line
     * @return the email's title block
     */
    static EmailBlock heading(String text) {
        return new Heading(text);
    }

    /**
     * @param text the paragraph's copy
     * @return a body paragraph
     */
    static EmailBlock paragraph(String text) {
        return new Paragraph(text);
    }

    /**
     * @param text the note's copy
     * @return a quieter paragraph, for expiry lines and "if this wasn't you" advice
     */
    static EmailBlock note(String text) {
        return new Note(text);
    }

    /**
     * @param label the button's label
     * @param url   the destination
     * @return a call-to-action button, followed by the same URL in full as a fallback
     */
    static EmailBlock button(String label, String url) {
        return new ButtonLink(label, url);
    }

    /**
     * @param code the code to display
     * @return a code the reader is expected to copy, set large and spaced
     */
    static EmailBlock code(String code) {
        return new CodeBlock(code);
    }

    /**
     * @param label the field name
     * @param value the field value
     * @return one labelled detail, such as the device a sign-in came from
     */
    static EmailBlock dataRow(String label, String value) {
        return new DataRow(label, value);
    }
}
