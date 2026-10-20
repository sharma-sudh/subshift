package com.subshift.backend.notifications.dto;

/** Outcome of one stale-subscription check. */
public record StaleCheckResult(int staleDays, int checked, int created, int skippedDuplicates) {
}
