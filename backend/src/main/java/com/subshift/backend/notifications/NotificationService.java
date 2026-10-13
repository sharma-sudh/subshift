package com.subshift.backend.notifications;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.notifications.dto.NotificationResponse;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String email) {
        User user = currentUserService.require(email);
        return notificationRepository.findByUserIdOrderBySentAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(String email, Integer notificationId) {
        User user = currentUserService.require(email);

        // A missing notification and someone else's notification both give 404, never 403.
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        // isRead is a Boolean wrapper in the entity, so Lombok generates setIsRead/getIsRead.
        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);

        return toResponse(saved);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getSentAt(),
                Boolean.TRUE.equals(notification.getIsRead())
        );
    }
}