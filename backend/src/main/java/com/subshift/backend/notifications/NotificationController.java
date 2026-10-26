package com.subshift.backend.notifications;

import com.subshift.backend.notifications.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    public record EmailPreference(boolean enabled) {}

    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        return notificationService.getNotifications(authentication.getName());
    }

    @GetMapping("/email-preference")
    public EmailPreference emailPreference(Authentication authentication) {
        return new EmailPreference(notificationService.getEmailPreference(authentication.getName()));
    }

    @PutMapping("/email-preference")
    public EmailPreference setEmailPreference(@RequestBody EmailPreference request, Authentication authentication) {
        return new EmailPreference(
                notificationService.setEmailPreference(authentication.getName(), request.enabled()));
    }

    /** Sends a test email to the logged-in user so the SMTP setup can be checked. */
    @PostMapping("/test-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testEmail(Authentication authentication) {
        notificationService.sendTestEmail(authentication.getName());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Integer id, Authentication authentication) {
        return notificationService.markAsRead(authentication.getName(), id);
    }
}