package com.tasklean.api.common.email;

/**
 * One composed email, ready to send: what it says, in both parts, and how much its flow depends on
 * it arriving. Built by {@link EmailTemplates} and handed to {@link EmailService}, which adds the
 * recipient.
 *
 * @param subject  the subject line — the only part safe to log, since bodies carry codes and links
 * @param text     the plain-text part, kept as the fallback for clients that will not render HTML
 * @param html     the HTML part, rendered from the same blocks as the text one
 * @param delivery what should happen if the provider refuses it
 */
public record EmailContent(String subject, String text, String html, Delivery delivery) {
}
