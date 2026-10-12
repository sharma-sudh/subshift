package com.subshift.backend.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByUserIdOrderBySentAtDesc(Integer userId);

    Optional<Notification> findByIdAndUserId(Integer id, Integer userId);
}