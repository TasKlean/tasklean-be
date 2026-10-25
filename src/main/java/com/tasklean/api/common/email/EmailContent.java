package com.tasklean.api.common.email;

/**
 * One composed email, ready to send: what it says and how much its flow depends on it arriving.
 * Built by {@link EmailTemplates} and handed to {@link EmailService}, which adds the recipient.
 *
 * @param subject  the subject line — the only part safe to log, since bodies carry codes and links
 * @param body     the plain-text body
 * @param delivery what should happen if the provider refuses it
 */
public record EmailContent(String subject, String body, Delivery delivery) {
}
