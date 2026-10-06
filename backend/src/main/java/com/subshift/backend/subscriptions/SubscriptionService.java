package com.subshift.backend.subscriptions;

import com.subshift.backend.catalog.Service;
import com.subshift.backend.catalog.ServiceRepository;
import com.subshift.backend.users.User;
import com.subshift.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Fully qualified because the catalog entity is also named Service.
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;

    public record SubscriptionResponse(Integer id, Integer userId, Integer serviceId, String serviceName,
                                       SubscriptionStatus status, LocalDate lastCheckedDate,
                                       LocalDateTime createdAt) {
        static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getUser().getId(), s.getService().getId(),
                    s.getService().getName(), s.getStatus(), s.getLastCheckedDate(), s.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(Integer userId) {
        List<Subscription> subs = (userId == null)
                ? subscriptionRepository.findAll()
                : subscriptionRepository.findByUserId(userId);
        return subs.stream().map(SubscriptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get(Integer id) {
        return SubscriptionResponse.from(find(id));
    }

    @Transactional
    public SubscriptionResponse create(Integer userId, Integer serviceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown userId"));
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown serviceId"));
        Subscription s = new Subscription();
        s.setUser(user);
        s.setService(service);
        try {
            return SubscriptionResponse.from(subscriptionRepository.saveAndFlush(s));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already subscribed to this service");
        }
    }

    @Transactional
    public SubscriptionResponse update(Integer id, SubscriptionStatus status, LocalDate lastCheckedDate) {
        Subscription s = find(id);
        if (status != null) s.setStatus(status);
        if (lastCheckedDate != null) s.setLastCheckedDate(lastCheckedDate);
        return SubscriptionResponse.from(subscriptionRepository.saveAndFlush(s));
    }

    @Transactional
    public void delete(Integer id) {
        subscriptionRepository.delete(find(id));
    }

    private Subscription find(Integer id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    }
}
