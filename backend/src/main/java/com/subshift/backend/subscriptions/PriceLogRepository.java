package com.subshift.backend.pricelog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceLogRepository extends JpaRepository<PriceLog, Long> {

    Optional<PriceLog> findTopBySubscriptionIdOrderByDateRecordedDescIdDesc(Long subscriptionId);
}
