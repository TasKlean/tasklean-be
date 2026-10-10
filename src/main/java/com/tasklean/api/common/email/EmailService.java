package com.tasklean.api.common.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Shared email infrastructure — used by auth verification, notifications, etc. */
@Service
public class EmailService {

    private static final String FROM_ADDRESS = "noreply@tasklean.app";

    private final JavaMailSender mailSender;
    private final String baseUrl;

    public EmailService(JavaMailSender mailSender, @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        // Strip any trailing slash so the link doesn't end up with a double slash.
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /**
     * Sends the email-verification message: a link to the frontend verify page (prefilled with the
     * email and code) plus the raw code as a manual-entry fallback. The link only lands on the page;
     * verification itself happens when the recipient clicks there, so a mail scanner prefetching the
     * link can't spend the single-use code.
     *
     * @param to   the recipient email address
     * @param code the verification code to send
     */
    public void sendVerificationEmail(String to, String code) {
        String link = baseUrl + "/verify-email?email=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                + "&code=" + code;
        send(to, "TasKlean - Verify your email",
                "Verify your email by opening this link:\n" + link
                        + "\n\nOr enter this code manually: " + code
                        + "\n\nThis code expires in 5 minutes.");
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(FROM_ADDRESS);
        mailSender.send(message);
    }
}
