package com.subshift.backend.dashboard;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.dashboard.DashboardRepository.CategoryInsight;
import com.subshift.backend.dashboard.DashboardRepository.CategorySpend;
import com.subshift.backend.dashboard.DashboardRepository.OverviewRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    public record SummaryResponse(BigDecimal totalMonthlySpend,
                                  List<CategorySpend> categoryBreakdown,
                                  List<OverviewRow> subscriptions) {}

    /** Interim shape for the Insights page; the Review 3 clustering will return the same fields. */
    public record InsightsResponse(List<CategoryInsight> clusters) {}

    private final DashboardRepository dashboardRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public SummaryResponse summary(String email) {
        Integer userId = currentUserService.require(email).getId();
        List<CategorySpend> breakdown = dashboardRepository.spendSummary(userId);
        // The total is derived from the same rows the chart shows, so the two can never disagree.
        BigDecimal total = breakdown.stream()
                .map(CategorySpend::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SummaryResponse(total, breakdown, dashboardRepository.activeSubscriptions(userId));
    }

    @Transactional(readOnly = true)
    public InsightsResponse insights(String email) {
        Integer userId = currentUserService.require(email).getId();
        return new InsightsResponse(dashboardRepository.categoryInsights(userId));
    }
}
