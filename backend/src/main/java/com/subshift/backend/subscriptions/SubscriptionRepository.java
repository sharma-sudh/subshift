package com.subshift.backend.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // Used by SubscriptionService.list(...)
    List<Subscription> findByUserId(Integer userId);

    // Used by ManualEntryService
    Optional<Subscription> findByUserIdAndServiceId(Integer userId, Integer serviceId);
}