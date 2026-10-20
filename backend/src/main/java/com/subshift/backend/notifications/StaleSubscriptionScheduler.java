package com.subshift.backend.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the stale-subscription check once a day. Both values can be overridden in application.yml or the environment. */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class StaleSubscriptionScheduler {

    private final StaleSubscriptionService staleSubscriptionService;

    @Value("${notifications.stale-days:60}")
    private int staleDays;

    @Scheduled(cron = "${notifications.stale-check-cron:0 0 9 * * *}")
    public void dailyCheck() {
        try {
            staleSubscriptionService.run(staleDays);
        } catch (RuntimeException e) {
            log.error("Scheduled stale-subscription check failed", e);
        }
    }
}
