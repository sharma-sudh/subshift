package com.subshift.backend.notifications;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.notifications.dto.NotificationResponse;
import com.subshift.backend.users.User;
import com.subshift.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final EmailNotifier emailNotifier;

    /**
     * The one place that creates a notification: saves the in-app row and, if the user has email alerts on,
     * emails it too. The email goes out only after the surrounding transaction commits, so a sync that is
     * rolled back never sends alerts about prices that were not saved.
     */
    public Notification create(User user, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        Notification saved = notificationRepository.save(notification);

        if (Boolean.TRUE.equals(user.getEmailAlertsEnabled())) {
            String to = user.getEmail();
            String name = user.getName();
            Runnable send = () -> emailNotifier.sendAlert(to, name, type, message);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        send.run();
                    }
                });
            } else {
                send.run();
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean getEmailPreference(String email) {
        return Boolean.TRUE.equals(currentUserService.require(email).getEmailAlertsEnabled());
    }

    @Transactional
    public boolean setEmailPreference(String email, boolean enabled) {
        User user = currentUserService.require(email);
        user.setEmailAlertsEnabled(enabled);
        userRepository.save(user);
        return enabled;
    }

    /** Sends a test message to the logged-in user's own address. */
    public void sendTestEmail(String email) {
        User user = currentUserService.require(email);
        emailNotifier.sendTest(user.getEmail(), user.getName());
    }

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