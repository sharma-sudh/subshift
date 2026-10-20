package com.subshift.backend.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // Used by SubscriptionService.list(...)
    List<Subscription> findByUserId(Integer userId);

    // Used by ManualEntryService
    Optional<Subscription> findByUserIdAndServiceId(Integer userId, Integer serviceId);

    // Used by StaleSubscriptionService: subscriptions with this status whose last check is before the cutoff
    // (rows with no last_checked_date are not included)
    @Query("select s from Subscription s join fetch s.user join fetch s.service "
            + "where s.status = :status and s.lastCheckedDate < :cutoff")
    List<Subscription> findStale(@Param("status") SubscriptionStatus status, @Param("cutoff") LocalDate cutoff);
}
