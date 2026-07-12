package com.tasklean.api.common.email;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Shared email infrastructure — used by auth verification, notifications, etc. */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String FROM_ADDRESS = "noreply@tasklean.app";

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String code) {
        send(to, "TasKlean - Verify your email",
                "Your verification code is: " + code + "\n\nThis code expires in 5 minutes.");
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
