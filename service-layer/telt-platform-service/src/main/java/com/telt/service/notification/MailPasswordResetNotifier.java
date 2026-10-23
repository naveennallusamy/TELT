package com.telt.service.notification;

import com.telt.config.properties.AccountProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends reset links via SMTP when {@code spring.mail.host} is configured; otherwise logs that mail is disabled.
 * The link itself is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailPasswordResetNotifier implements PasswordResetNotifier {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AccountProperties accountProperties;

    @Override
    public void sendPasswordResetLink(String email, String resetLink) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mail is not configured (spring.mail.host); password reset e-mail was not sent");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(accountProperties.mailFrom());
        message.setTo(email);
        message.setSubject("Reset your TELT password");
        message.setText("Use the link below to set a new password. It expires in "
                + accountProperties.passwordResetTtl().toMinutes() + " minutes and can be used once.\n\n" + resetLink
                + "\n\nIf you did not request this, you can ignore this e-mail.");
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("Failed to send password reset e-mail", e);
        }
    }
}
