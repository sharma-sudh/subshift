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
<<<<<<< HEAD
                         int ignoredNonSubscription, LocalDateTime lastSyncedAt, List<Outcome> outcomes) {
=======
                         LocalDateTime lastSyncedAt, List<Outcome> outcomes) {
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505

    public record Outcome(String messageId, String subject, String serviceName, String result) {}
}
