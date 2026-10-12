package com.subshift.backend.email;

import java.time.LocalDateTime;
import java.util.List;

/** What a sync did. emailsRemaining > 0 means run the sync again to continue. */
public record SyncReport(int emailsProcessed, int emailsRemaining, boolean stoppedEarly, String stopReason,
                         int pricesRecorded, int hikesNotified, int newSubscriptions, int newServices,
                         LocalDateTime lastSyncedAt, List<Outcome> outcomes) {

    public record Outcome(String messageId, String subject, String serviceName, String result) {}
}
