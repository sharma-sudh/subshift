package com.subshift.backend.subscriptions.dto;

import com.subshift.backend.subscriptions.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualEntryResponse(
        Integer subscriptionId,
        Integer serviceId,
        String serviceName,
        String category,
        SubscriptionStatus status,
        BigDecimal latestPrice,
        LocalDate lastCheckedDate
) {
}