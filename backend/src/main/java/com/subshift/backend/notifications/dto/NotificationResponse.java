package com.subshift.backend.notifications.dto;

import com.subshift.backend.notifications.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Integer id,
        NotificationType type,
        String message,
        LocalDateTime sentAt,
        boolean isRead
) {
}