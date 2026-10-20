package com.subshift.backend.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByUserIdOrderBySentAtDesc(Integer userId);

    Optional<Notification> findByIdAndUserId(Integer id, Integer userId);

    // Used by StaleSubscriptionService to avoid repeating the same notification
    boolean existsByUserIdAndTypeAndMessageAndSentAtAfter(Integer userId, NotificationType type,
                                                          String message, LocalDateTime after);
}
