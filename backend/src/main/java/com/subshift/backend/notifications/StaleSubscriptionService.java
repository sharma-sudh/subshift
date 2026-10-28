package com.subshift.backend.notifications;

import com.subshift.backend.common.AppTime;
import com.subshift.backend.notifications.dto.StaleCheckResult;
import com.subshift.backend.subscriptions.Subscription;
import com.subshift.backend.subscriptions.SubscriptionRepository;
import com.subshift.backend.subscriptions.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Finds ACTIVE subscriptions with no renewal email or manual entry for a long time and raises a
 * POSSIBLE_CANCELLATION notification. It never changes the subscription's status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaleSubscriptionService {

    /** Notification copy. Change the wording here. Arguments: service name, number of days. */
    static final String CANCELLATION_MESSAGE = "No renewal email seen for %s in over %d days. Did you cancel it?";

    /** The notifications table has no subscription column, so the same message is not repeated within this window. */
    static final int DEDUP_WINDOW_DAYS = 30;

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Transactional
    public StaleCheckResult run(int staleDays) {
        LocalDate cutoff = AppTime.today().minusDays(staleDays);
        LocalDateTime windowStart = LocalDateTime.now().minusDays(DEDUP_WINDOW_DAYS);

        List<Subscription> stale = subscriptionRepository.findStale(SubscriptionStatus.ACTIVE, cutoff);
        int created = 0;
        int skipped = 0;
        for (Subscription subscription : stale) {
            String message = String.format(CANCELLATION_MESSAGE, subscription.getService().getName(), staleDays);
            boolean alreadySent = notificationRepository.existsByUserIdAndTypeAndMessageAndSentAtAfter(
                    subscription.getUser().getId(), NotificationType.POSSIBLE_CANCELLATION, message, windowStart);
            if (alreadySent) {
                skipped++;
                continue;
            }
            // Saves the in-app notification and emails it when the user has email alerts on.
            notificationService.create(subscription.getUser(), NotificationType.POSSIBLE_CANCELLATION, message);
            created++;
        }
        log.info("Stale-subscription check ({} days): {} stale, {} notifications created, {} duplicates skipped",
                staleDays, stale.size(), created, skipped);
        return new StaleCheckResult(staleDays, stale.size(), created, skipped);
    }
}
