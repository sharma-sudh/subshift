package com.subshift.backend.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    List<Subscription> findByUserId(Integer userId);
}
