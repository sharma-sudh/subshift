package com.subshift.backend.notifications;

import com.subshift.backend.notifications.dto.StaleCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin trigger for the stale-subscription check, so it can be demonstrated without waiting for the daily run. */
@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final StaleSubscriptionService staleSubscriptionService;

    @Value("${notifications.stale-days:60}")
    private int defaultStaleDays;

    @PostMapping("/stale-check")
    public StaleCheckResult staleCheck(@RequestParam(required = false) Integer days) {
        int staleDays = days != null ? days : defaultStaleDays;
        if (staleDays < 1 || staleDays > 3650) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 3650");
        }
        return staleSubscriptionService.run(staleDays);
    }
}
