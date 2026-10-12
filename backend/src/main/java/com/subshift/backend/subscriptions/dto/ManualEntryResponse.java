package com.subshift.backend.subscriptions.dto;

import com.subshift.backend.subscriptions.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualEntryResponse(
        Long subscriptionId,
        Long serviceId,
        String serviceName,
        String category,
        SubscriptionStatus status,
        BigDecimal latestPrice,
        LocalDate lastCheckedDate
) {
}
