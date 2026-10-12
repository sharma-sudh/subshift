package com.subshift.backend.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserIdAndServiceId(Long userId, Long serviceId);
}
