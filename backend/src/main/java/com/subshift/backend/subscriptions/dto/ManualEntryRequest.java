package com.subshift.backend.subscriptions.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualEntryRequest(
        @NotNull(message = "serviceId is required")
        Integer serviceId,

        @NotNull(message = "price is required")
        @Positive(message = "price must be positive")
        BigDecimal price,

        @NotNull(message = "dateRecorded is required")
        @PastOrPresent(message = "dateRecorded cannot be in the future")
        LocalDate dateRecorded
) {
}