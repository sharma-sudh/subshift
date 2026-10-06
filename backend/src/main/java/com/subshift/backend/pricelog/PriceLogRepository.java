package com.subshift.backend.pricelog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceLogRepository extends JpaRepository<PriceLog, Integer> {
}
