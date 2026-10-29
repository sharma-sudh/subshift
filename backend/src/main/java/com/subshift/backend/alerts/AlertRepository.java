package com.subshift.backend.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Integer> {

    List<Alert> findBySubscriptionUserIdOrderByDetectedAtDesc(Integer userId);

    Optional<Alert> findByIdAndSubscriptionUserId(Integer id, Integer userId);

    @Query("select count(a) from Alert a where a.subscription.user.id = :userId and a.isRead = false")
    long countUnread(@Param("userId") Integer userId);

    /**
     * Calls the mark_all_alerts_read stored procedure (db/schema.sql). A stored procedure is not a JPA entity
     * operation, so this is one of the places where raw SQL is needed. clearAutomatically drops stale cached alerts.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "CALL mark_all_alerts_read(:userId)", nativeQuery = true)
    void markAllReadViaProcedure(@Param("userId") Integer userId);
}
