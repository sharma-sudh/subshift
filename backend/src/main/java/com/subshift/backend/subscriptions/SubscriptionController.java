package com.subshift.backend.subscriptions;

import com.subshift.backend.subscriptions.SubscriptionService.SubscriptionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // userId comes from the body/query for now; replace with the authenticated user once auth lands.
    public record CreateRequest(@NotNull Integer userId, @NotNull Integer serviceId) {}

    public record UpdateRequest(SubscriptionStatus status, LocalDate lastCheckedDate) {}

    @GetMapping
    public List<SubscriptionResponse> list(@RequestParam(required = false) Integer userId) {
        return subscriptionService.list(userId);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@PathVariable Integer id) {
        return subscriptionService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@Valid @RequestBody CreateRequest req) {
        return subscriptionService.create(req.userId(), req.serviceId());
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@PathVariable Integer id, @RequestBody UpdateRequest req) {
        return subscriptionService.update(id, req.status(), req.lastCheckedDate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        subscriptionService.delete(id);
    }
}
