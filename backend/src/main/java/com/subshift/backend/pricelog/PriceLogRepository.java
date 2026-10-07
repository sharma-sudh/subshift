package com.subshift.backend.pricelog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceLogRepository extends JpaRepository<PriceLog, Integer> {

    /** Latest price entry; ties on date are broken by the newest id. */
    Optional<PriceLog> findTopBySubscriptionIdOrderByDateRecordedDescIdDesc(Integer subscriptionId);

    /** Full history, oldest first, for charting. */
    List<PriceLog> findBySubscriptionIdOrderByDateRecordedAscIdAsc(Integer subscriptionId);
}
