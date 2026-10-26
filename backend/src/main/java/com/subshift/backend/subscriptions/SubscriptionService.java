package com.subshift.backend.subscriptions;

import com.subshift.backend.catalog.Service;
import com.subshift.backend.catalog.ServiceRepository;
import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.pricelog.PriceLog;
import com.subshift.backend.pricelog.PriceLogRepository;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Fully qualified because the catalog entity is also named Service.
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ServiceRepository serviceRepository;
    private final PriceLogRepository priceLogRepository;
    private final CurrentUserService currentUserService;

    public record SubscriptionResponse(Integer id, Integer serviceId, String serviceName, String category,
                                       SubscriptionStatus status, BigDecimal currentPrice,
                                       LocalDate lastCheckedDate, LocalDateTime createdAt, String planName) {}

    public record PricePoint(BigDecimal price, LocalDate dateRecorded) {}

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(String email) {
        User user = currentUserService.require(email);
        return subscriptionRepository.findByUserId(user.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get(String email, Integer id) {
        return toResponse(findOwned(email, id));
    }

    @Transactional(readOnly = true)
    public List<PricePoint> priceHistory(String email, Integer id) {
        Subscription s = findOwned(email, id);
        return priceLogRepository.findBySubscriptionIdOrderByDateRecordedAscIdAsc(s.getId()).stream()
                .map(p -> new PricePoint(p.getPrice(), p.getDateRecorded())).toList();
    }

    @Transactional
    public SubscriptionResponse create(String email, Integer serviceId) {
        User user = currentUserService.require(email);
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown serviceId"));
        Subscription s = new Subscription();
        s.setUser(user);
        s.setService(service);
        try {
            return toResponse(subscriptionRepository.saveAndFlush(s));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already subscribed to this service");
        }
    }

    @Transactional
    public SubscriptionResponse update(String email, Integer id, SubscriptionStatus status, LocalDate lastCheckedDate) {
        Subscription s = findOwned(email, id);
        if (status != null) s.setStatus(status);
        if (lastCheckedDate != null) s.setLastCheckedDate(lastCheckedDate);
        return toResponse(subscriptionRepository.saveAndFlush(s));
    }

    @Transactional
    public void delete(String email, Integer id) {
        subscriptionRepository.delete(findOwned(email, id));
    }

    /** Someone else's subscription looks exactly like a missing one: 404, not 403. */
    private Subscription findOwned(String email, Integer id) {
        User user = currentUserService.require(email);
        return subscriptionRepository.findById(id)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    }

    private SubscriptionResponse toResponse(Subscription s) {
        BigDecimal currentPrice = priceLogRepository
                .findTopBySubscriptionIdOrderByDateRecordedDescIdDesc(s.getId())
                .map(PriceLog::getPrice).orElse(null);
        return new SubscriptionResponse(s.getId(), s.getService().getId(), s.getService().getName(),
                s.getService().getCategory().getName(), s.getStatus(), currentPrice,
                s.getLastCheckedDate(), s.getCreatedAt(), s.getPlanName());
    }
}
