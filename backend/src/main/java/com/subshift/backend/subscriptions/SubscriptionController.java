package com.subshift.backend.subscriptions;

import com.subshift.backend.subscriptions.SubscriptionService.PricePoint;
import com.subshift.backend.subscriptions.SubscriptionService.SubscriptionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public record CreateRequest(@NotNull Integer serviceId) {}

    public record UpdateRequest(SubscriptionStatus status, LocalDate lastCheckedDate) {}

    @GetMapping
    public List<SubscriptionResponse> list(Authentication authentication) {
        return subscriptionService.list(authentication.getName());
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@PathVariable Integer id, Authentication authentication) {
        return subscriptionService.get(authentication.getName(), id);
    }

    @GetMapping("/{id}/price-history")
    public List<PricePoint> priceHistory(@PathVariable Integer id, Authentication authentication) {
        return subscriptionService.priceHistory(authentication.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@Valid @RequestBody CreateRequest req, Authentication authentication) {
        return subscriptionService.create(authentication.getName(), req.serviceId());
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@PathVariable Integer id, @RequestBody UpdateRequest req,
                                       Authentication authentication) {
        return subscriptionService.update(authentication.getName(), id, req.status(), req.lastCheckedDate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, Authentication authentication) {
        subscriptionService.delete(authentication.getName(), id);
    }
}
