package com.subshift.backend.dashboard;

import com.subshift.backend.dashboard.DashboardService.InsightsResponse;
import com.subshift.backend.dashboard.DashboardService.SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public SummaryResponse summary(Authentication authentication) {
        return dashboardService.summary(authentication.getName());
    }

    @GetMapping("/insights")
    public InsightsResponse insights(Authentication authentication) {
        return dashboardService.insights(authentication.getName());
    }
}
