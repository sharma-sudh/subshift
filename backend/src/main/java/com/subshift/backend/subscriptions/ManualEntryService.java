package com.subshift.backend.subscriptions;

import com.subshift.backend.catalog.ServiceRepository;
import com.subshift.backend.pricelog.PriceLog;
import com.subshift.backend.pricelog.PriceLogRepository;
import com.subshift.backend.pricelog.PriceSource;
import com.subshift.backend.subscriptions.dto.ManualEntryRequest;
import com.subshift.backend.subscriptions.dto.ManualEntryResponse;
import com.subshift.backend.users.User;
import com.subshift.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ManualEntryService {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PriceLogRepository priceLogRepository;

    /**
     * Result of a manual entry: the response body plus whether a new
     * subscription was created (controller maps this to 201 vs 200).
     */
    public record Result(ManualEntryResponse response, boolean created) {
    }

    @Transactional
    public Result addManualEntry(String email, ManualEntryRequest request) {
        // 1. Resolve current user and service
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        com.subshift.backend.catalog.Service service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        LocalDate today = LocalDate.now();

        // 2. Find or create the subscription for this user + service
        Optional<Subscription> existing =
                subscriptionRepository.findByUserIdAndServiceId(user.getId(), service.getId());

        boolean created = existing.isEmpty();
        Subscription subscription;
        if (created) {
            subscription = new Subscription();
            subscription.setUser(user);
            subscription.setService(service);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setLastCheckedDate(today);
            subscription = subscriptionRepository.save(subscription);
        } else {
            subscription = existing.get();
        }

        // 3. Insert a price log only if the price changed (or none exists yet)
        Optional<PriceLog> latest = priceLogRepository
                .findTopBySubscriptionIdOrderByDateRecordedDescIdDesc(subscription.getId());

        boolean priceChanged = latest.isEmpty()
                || latest.get().getPrice().compareTo(request.price()) != 0;

        if (priceChanged) {
            PriceLog priceLog = new PriceLog();
            priceLog.setSubscription(subscription);
            priceLog.setPrice(request.price());
            priceLog.setDateRecorded(request.dateRecorded());
            priceLog.setSource(PriceSource.MANUAL);
            priceLogRepository.save(priceLog);
        }

        // 4. Update lastCheckedDate
        subscription.setLastCheckedDate(today);
        subscription = subscriptionRepository.save(subscription);

        // Re-read the latest price (a backdated entry may not be the latest by date)
        PriceLog latestAfter = priceLogRepository
                .findTopBySubscriptionIdOrderByDateRecordedDescIdDesc(subscription.getId())
                .orElseThrow(() -> new IllegalStateException("Price log missing after save"));

        ManualEntryResponse response = new ManualEntryResponse(
                subscription.getId(),
                service.getId(),
                service.getName(),
                service.getCategory().getName(),
                subscription.getStatus(),
                latestAfter.getPrice(),
                subscription.getLastCheckedDate()
        );

        return new Result(response, created);
    }
}
