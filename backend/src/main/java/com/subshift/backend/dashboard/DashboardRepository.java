package com.subshift.backend.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reads the reporting objects that live in the database (see db/schema.sql):
 * the active_subscriptions_overview VIEW and the get_user_spend_summary() stored function.
 * Plain parameterised SQL through JdbcTemplate, because a view and a function are not JPA entities.
 */
@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    public record CategorySpend(String category, BigDecimal amount) {}

    public record OverviewRow(Integer id, String serviceName, String category, String status,
                              BigDecimal currentPrice, LocalDate lastCheckedDate, LocalDate priceLastUpdated,
                              String planName) {}

    public record CategoryInsight(String category, BigDecimal totalSpend, int subscriptionCount) {}

    private final JdbcTemplate jdbc;

    /** Stored function: latest price of each ACTIVE subscription, summed per category. */
    public List<CategorySpend> spendSummary(Integer userId) {
        return jdbc.query(
                "SELECT category_name, total_spend FROM get_user_spend_summary(?) ORDER BY total_spend DESC",
                (rs, i) -> new CategorySpend(rs.getString("category_name"), rs.getBigDecimal("total_spend")),
                userId);
    }

    /** View: one row per ACTIVE subscription with its latest price (null if no price recorded yet). */
    public List<OverviewRow> activeSubscriptions(Integer userId) {
        return jdbc.query(
                "SELECT subscription_id, service_name, category_name, status, current_price, "
                        + "last_checked_date, price_last_updated, plan_name "
                        + "FROM active_subscriptions_overview WHERE user_id = ? ORDER BY service_name",
                (rs, i) -> new OverviewRow(
                        rs.getInt("subscription_id"),
                        rs.getString("service_name"),
                        rs.getString("category_name"),
                        rs.getString("status"),
                        rs.getBigDecimal("current_price"),
                        rs.getObject("last_checked_date", LocalDate.class),
                        rs.getObject("price_last_updated", LocalDate.class),
                        rs.getString("plan_name")),
                userId);
    }

    /** Spend and number of priced subscriptions per category, biggest first. */
    public List<CategoryInsight> categoryInsights(Integer userId) {
        return jdbc.query(
                "SELECT category_name, COALESCE(SUM(current_price), 0) AS total_spend, "
                        + "COUNT(current_price) AS subscription_count "
                        + "FROM active_subscriptions_overview WHERE user_id = ? "
                        + "GROUP BY category_name HAVING COUNT(current_price) > 0 "
                        + "ORDER BY total_spend DESC",
                (rs, i) -> new CategoryInsight(
                        rs.getString("category_name"),
                        rs.getBigDecimal("total_spend"),
                        rs.getInt("subscription_count")),
                userId);
    }
}
