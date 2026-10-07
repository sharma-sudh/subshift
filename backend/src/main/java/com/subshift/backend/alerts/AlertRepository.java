package com.subshift.backend.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Integer> {

    List<Alert> findBySubscriptionUserIdOrderByDetectedAtDesc(Integer userId);

    Optional<Alert> findByIdAndSubscriptionUserId(Integer id, Integer userId);
}
