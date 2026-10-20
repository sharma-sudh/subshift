package com.subshift.backend.dashboard;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.dashboard.DashboardRepository.CategoryInsight;
import com.subshift.backend.dashboard.DashboardRepository.CategorySpend;
import com.subshift.backend.dashboard.DashboardRepository.OverviewRow;
import com.subshift.backend.ml.SpendClusteringService;
import com.subshift.backend.ml.SpendClusteringService.PricedItem;
import com.subshift.backend.ml.SpendClusteringService.SpendTiers;
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

    /**
     * {@code clusters} is the per-category spend the Insights page already draws. {@code spendTiers} is the k-means
     * grouping of the user's subscriptions by price, with its silhouette score.
     */
    public record InsightsResponse(List<CategoryInsight> clusters, SpendTiers spendTiers) {}

    private final DashboardRepository dashboardRepository;
    private final CurrentUserService currentUserService;
    private final SpendClusteringService spendClusteringService;

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
        List<PricedItem> priced = dashboardRepository.activeSubscriptions(userId).stream()
                .filter(row -> row.currentPrice() != null)
                .map(row -> new PricedItem(row.serviceName(), row.category(), row.currentPrice()))
                .toList();
        return new InsightsResponse(dashboardRepository.categoryInsights(userId),
                spendClusteringService.cluster(priced));
    }
}
