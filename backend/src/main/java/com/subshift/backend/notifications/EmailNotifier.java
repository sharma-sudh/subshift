package com.subshift.backend.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends alert emails over SMTP (spring.mail.* in application.yml). Nothing is sent unless
 * notifications.email.enabled is true and an SMTP host and sender address are configured, so the app still
 * runs fine on a machine with no mail settings.
 */
@Slf4j
@Component
public class EmailNotifier {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String host;
    private final String from;
    private final String frontendUrl;

    public EmailNotifier(ObjectProvider<JavaMailSender> mailSender,
                         @Value("${notifications.email.enabled:false}") boolean enabled,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${notifications.email.from:}") String configuredFrom,
                         @Value("${spring.mail.username:}") String username,
                         @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.host = host == null ? "" : host.trim();
        this.from = configuredFrom != null && !configuredFrom.isBlank() ? configuredFrom.trim()
                : (username == null ? "" : username.trim());
        this.frontendUrl = frontendUrl;
    }

    public boolean isAvailable() {
        return enabled && !host.isEmpty() && !from.isEmpty() && mailSender.getIfAvailable() != null;
    }

    /**
     * Fire-and-forget: runs on a background thread, never throws, and never blocks a sync or the scheduler.
     * The arguments are plain values (not entities) because this runs outside the caller's transaction.
     */
    @Async
    public void sendAlert(String to, String name, NotificationType type, String message) {
        if (!isAvailable() || to == null || to.isBlank()) {
            return;
        }
        try {
            deliver(to, "SubShift alert: " + describe(type), body(name, message));
        } catch (Exception e) {
            // Class name only: the message of a mail exception can contain the SMTP server's reply.
            log.warn("Could not send alert email: {}", e.getClass().getSimpleName());
        }
    }

    /** Sends a test message and reports failure to the caller (used by the "Send test email" button). */
    public void sendTest(String to, String name) {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email alerts are not configured on the server (set MAIL_ENABLED, MAIL_HOST and the SMTP login)");
        }
        try {
            deliver(to, "SubShift test email",
                    body(name, "This is a test message. If you can read it, SubShift can email your alerts."));
        } catch (MailException e) {
            log.warn("Test email failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The mail server did not accept the message");
        }
    }

    private void deliver(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.getObject().send(message);
    }

    private String body(String name, String message) {
        return "Hi " + (name == null || name.isBlank() ? "there" : name) + ",\n\n"
                + message + "\n\n"
                + "Review your subscriptions: " + frontendUrl + "/alerts\n\n"
                + "You get this email because email alerts are switched on for your SubShift account. "
                + "You can switch them off on the Alerts page.\n";
    }

    private static String describe(NotificationType type) {
        return switch (type) {
            case PRICE_HIKE -> "price increase";
            case PRICE_DROP -> "price drop";
            case POSSIBLE_CANCELLATION -> "possible cancellation";
        };
    }
}
