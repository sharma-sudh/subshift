package com.subshift.backend.email;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What a sync did. emailsRemaining > 0 means run the sync again to continue.
 * ignoredNonSubscription counts emails that were checked and turned out not to be subscription renewals
 * (one-time purchases, promotions, OTPs ...). They are only counted, never listed in outcomes.
 */
public record SyncReport(int emailsProcessed, int emailsRemaining, boolean stoppedEarly, String stopReason,
                         int pricesRecorded, int hikesNotified, int dropsNotified, int newSubscriptions, int newServices,
                         int ignoredNonSubscription, LocalDateTime lastSyncedAt, List<Outcome> outcomes) {

    public record Outcome(String messageId, String subject, String serviceName, String result) {}
}
