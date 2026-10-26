package com.subshift.backend.email;

import com.subshift.backend.catalog.Category;
import com.subshift.backend.catalog.CategoryRepository;
import com.subshift.backend.catalog.Service;
import com.subshift.backend.catalog.ServiceRepository;
import com.subshift.backend.email.EmailExtractionService.ScanItem;
import com.subshift.backend.email.SyncReport.Outcome;
import com.subshift.backend.gmail.GmailConnectionRepository;
import com.subshift.backend.notifications.NotificationService;
import com.subshift.backend.notifications.NotificationType;
import com.subshift.backend.pricelog.PriceLog;
import com.subshift.backend.pricelog.PriceLogRepository;
import com.subshift.backend.pricelog.PriceSource;
import com.subshift.backend.subscriptions.Subscription;
import com.subshift.backend.subscriptions.SubscriptionRepository;
import com.subshift.backend.users.User;
import com.subshift.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single transactional write step: extraction results -> services, subscriptions, price_log,
 * alerts (created by the DB trigger on price_log insert), notifications and the sync watermark.
 * Either everything in a batch is saved or nothing is. No network calls happen in here, so the
 * transaction stays short.
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class EmailSyncPersister {

    /** Notification copy. Change the wording here. Arguments: service name, old price, new price. */
    static final String HIKE_MESSAGE = "%s price increased from \u20B9%s to \u20B9%s";
    static final String DROP_MESSAGE = "%s price dropped from \u20B9%s to \u20B9%s";
<<<<<<< HEAD
    /** Arguments: service name, old plan, new plan, old price, new price. */
    static final String PLAN_CHANGE_MESSAGE = "%s plan changed from %s to %s (\u20B9%s to \u20B9%s)";

    /**
     * Results for emails that are not subscription renewals at all: promotions, OTPs, one-time purchases.
     * They are counted but never listed, so the sync details only show real subscription activity.
     */
    private static final Set<String> NOT_A_SUBSCRIPTION =
            Set.of("skipped_by_classifier", "skipped_not_renewal", "skipped_not_recurring");
=======
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505

    private static final String CURRENCY = "INR";
    private static final String FALLBACK_CATEGORY = "Other";
    /** Services are shared by every user, so cap how many one sync may create. */
    private static final int MAX_NEW_SERVICES_PER_SYNC = 5;

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PriceLogRepository priceLogRepository;
    private final NotificationService notificationService;
    private final GmailConnectionRepository gmailConnectionRepository;
    private final ServiceMatcher serviceMatcher;

    /** newLastSyncedAt may be null, which leaves the watermark unchanged. */
    @Transactional
    public SyncReport persist(Integer userId, List<ScanItem> items, LocalDateTime newLastSyncedAt) {
        User user = userRepository.findById(userId).orElseThrow();
        Run run = new Run(user);

        // Oldest first, so a price rise is detected in the order it really happened.
        List<ScanItem> ordered = items.stream()
                .sorted(Comparator.comparing((ScanItem i) -> effectiveDate(i, run.today)))
                .toList();
        List<Outcome> outcomes = new ArrayList<>();
        int ignored = 0;
        for (ScanItem item : ordered) {
            String result = run.handle(item);
            if (NOT_A_SUBSCRIPTION.contains(result)) {
                ignored++;
                continue;
            }
            outcomes.add(new Outcome(item.messageId(), item.subject(), item.serviceName(), result));
        }

        if (newLastSyncedAt != null) {
            gmailConnectionRepository.findByUserId(userId).ifPresent(c -> c.setLastSyncedAt(newLastSyncedAt));
        }
        return new SyncReport(0, 0, false, null, run.prices, run.hikes, run.drops, run.newSubscriptions,
<<<<<<< HEAD
                run.newServices, ignored, newLastSyncedAt, outcomes);
=======
                run.newServices, newLastSyncedAt, outcomes);
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505
    }

    /** A date in the future is an upcoming-renewal notice, so use the day the email arrived instead. */
    private static LocalDate effectiveDate(ScanItem item, LocalDate today) {
        LocalDate date = item.billingDate();
        return (date == null || date.isAfter(today)) ? item.emailDate() : date;
    }

    private final class Run {
        final User user;
        final LocalDate today = LocalDate.now();
        final List<Service> known = new ArrayList<>(serviceRepository.findAll());
        final Map<Integer, Subscription> subscriptions = new HashMap<>();
        Category fallbackCategory;
        int prices;
        int hikes;
        int drops;
        int newSubscriptions;
        int newServices;

        Run(User user) {
            this.user = user;
            for (Subscription s : subscriptionRepository.findByUserId(user.getId())) {
                subscriptions.put(s.getService().getId(), s);
            }
        }

        String handle(ScanItem item) {
            if (ScanItem.CLASSIFIER_MODEL.equals(item.model())) {
                return "skipped_by_classifier";
            }
            if (item.error() != null) {
                return "extraction_failed";
            }
            if (!item.isRenewal()) {
                return "skipped_not_renewal";
            }
            // A failed or pending payment is not a price. A charge that does not repeat (a one-off purchase) is not a
            // subscription, even if the model called the email a renewal.
            if (item.paymentFailed()) {
                return "skipped_payment_failed";
            }
            if (!item.recurring()) {
                return "skipped_not_recurring";
            }
            if (item.price() == null) {
                return "skipped_no_price";
            }
            // price_log has no currency column, so only rupee prices can be stored safely.
            if (!CURRENCY.equals(item.currency())) {
                return "skipped_currency";
            }
            if (item.serviceName() == null) {
                return "skipped_no_service_name";
            }

            // "Claude", "Claude Pro" and "Claude Plus" are one service with different plans; names that differ in
            // the brand ("YouTube Music" and "YouTube Premium") stay separate services.
            Service service;
            String plan;
            ServiceMatcher.Match match = serviceMatcher.matchWithPlan(item.serviceName(), known).orElse(null);
            if (match != null) {
                service = match.service();
                plan = match.plan();
            } else {
                if (!serviceMatcher.isValidName(item.serviceName())) {
                    return "skipped_invalid_service_name";
                }
                if (newServices >= MAX_NEW_SERVICES_PER_SYNC) {
                    return "skipped_new_service_limit";
                }
                Category category = fallbackCategory();
                if (category == null) {
                    return "skipped_no_other_category";
                }
                service = new Service();
                // The catalog is shared, so it stores the brand ("Claude"), not whichever plan was seen first.
                service.setName(serviceMatcher.catalogName(item.serviceName()));
                service.setCategory(category);
                service = serviceRepository.save(service);
                known.add(service);
                newServices++;
                plan = serviceMatcher.matchWithPlan(item.serviceName(), List.of(service))
                        .map(ServiceMatcher.Match::plan).orElse(null);
            }
            if (plan == null) {
                plan = serviceMatcher.cleanPlan(item.planName()); // the plan the model read from the email
            }

            LocalDate date = effectiveDate(item, today);
            Subscription subscription = subscriptions.get(service.getId());
            if (subscription == null) {
                subscription = new Subscription();
                subscription.setUser(user);
                subscription.setService(service);
                subscription.setLastCheckedDate(date);
                subscription = subscriptionRepository.save(subscription);
                subscriptions.put(service.getId(), subscription);
                newSubscriptions++;
            }

            Optional<PriceLog> latest = priceLogRepository
                    .findTopBySubscriptionIdOrderByDateRecordedDescIdDesc(subscription.getId());
            if (latest.isPresent() && date.isBefore(latest.get().getDateRecorded())) {
                return "skipped_older_than_current"; // history, not the current price
            }

            // The plan is only updated from an email that is not older than what we already know.
            String previousPlan = subscription.getPlanName();
            boolean planChanged = plan != null && previousPlan != null && !plan.equalsIgnoreCase(previousPlan);
            if (plan != null && !plan.equalsIgnoreCase(previousPlan)) {
                subscription.setPlanName(plan);
            }
            String label = service.getName() + (plan == null ? "" : " (" + plan + ")");

            if (latest.isPresent() && latest.get().getPrice().compareTo(item.price()) == 0) {
                touch(subscription, date);
                return "unchanged"; // price_log only stores price changes
            }

            PriceLog entry = new PriceLog();
            entry.setSubscription(subscription);
            entry.setPrice(item.price());
            entry.setDateRecorded(date);
            entry.setSource(PriceSource.EMAIL_PARSED);
            priceLogRepository.save(entry); // the DB trigger inserts the alert when the price changed (up or down)
            prices++;
            touch(subscription, date);

            if (latest.isPresent()) {
                int direction = item.price().compareTo(latest.get().getPrice());
                boolean hike = direction > 0;
<<<<<<< HEAD
                String oldPrice = latest.get().getPrice().toPlainString();
                String newPrice = item.price().toPlainString();
                String message = planChanged
                        ? String.format(PLAN_CHANGE_MESSAGE, service.getName(), previousPlan, plan, oldPrice, newPrice)
                        : String.format(hike ? HIKE_MESSAGE : DROP_MESSAGE, label, oldPrice, newPrice);
                // Saves the in-app notification and emails it when the user has email alerts on.
                notificationService.create(user, hike ? NotificationType.PRICE_HIKE : NotificationType.PRICE_DROP,
                        message);
=======
                Notification notification = new Notification();
                notification.setUser(user);
                notification.setType(hike ? NotificationType.PRICE_HIKE : NotificationType.PRICE_DROP);
                notification.setMessage(String.format(hike ? HIKE_MESSAGE : DROP_MESSAGE, service.getName(),
                        latest.get().getPrice().toPlainString(), item.price().toPlainString()));
                notificationRepository.save(notification);
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505
                if (hike) {
                    hikes++;
                    return "price_hike_notified";
                }
                drops++;
                return "price_drop_notified";
            }
            return "price_recorded";
        }

        private void touch(Subscription subscription, LocalDate date) {
            if (subscription.getLastCheckedDate() == null || date.isAfter(subscription.getLastCheckedDate())) {
                subscription.setLastCheckedDate(date);
            }
        }

        private Category fallbackCategory() {
            if (fallbackCategory == null) {
                fallbackCategory = categoryRepository.findByName(FALLBACK_CATEGORY).orElse(null);
            }
            return fallbackCategory;
        }
    }
}
