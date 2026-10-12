package com.subshift.backend.notifications;

import com.subshift.backend.notifications.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        return notificationService.getNotifications(authentication.getName());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Integer id, Authentication authentication) {
        return notificationService.markAsRead(authentication.getName(), id);
    }
}