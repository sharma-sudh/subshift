package com.subshift.backend.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the price histories the forecast model trains on: every real subscription's price_log, plus the
 * real price histories bundled in src/main/resources/ml (prices in rupees; sources and dates are listed per row
 * in data/price_history_sources.csv). No generated data is used.
 */
@Slf4j
@Component
public class PriceTrainingData {

    private static final String BUNDLED_RESOURCE = "ml/price_history_real_inr.csv";

    private final JdbcTemplate jdbc;

    public PriceTrainingData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PriceSeries> loadAll() {
        List<PriceSeries> all = new ArrayList<>(loadReal());
        all.addAll(loadBundled());
        return all;
    }

    /** One series per subscription. Subscriptions to the same service share a group for cross-validation. */
    List<PriceSeries> loadReal() {
        Map<Integer, Builder> builders = new LinkedHashMap<>();
        jdbc.query(
                "SELECT s.id AS subscription_id, sv.id AS service_id, c.name AS category, "
                        + "pl.date_recorded, pl.price "
                        + "FROM price_log pl "
                        + "JOIN subscriptions s ON s.id = pl.subscription_id "
                        + "JOIN services sv ON sv.id = s.service_id "
                        + "JOIN categories c ON c.id = sv.category_id "
                        + "ORDER BY s.id, pl.date_recorded, pl.id",
                rs -> {
                    Builder builder = builders.computeIfAbsent(rs.getInt("subscription_id"),
                            id -> {
                                try {
                                    return new Builder("real-service-" + rs.getInt("service_id"), rs.getString("category"));
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                    builder.add(rs.getObject("date_recorded", LocalDate.class), rs.getDouble("price"));
                });
        return builders.values().stream().map(Builder::build).toList();
    }

    /** The bundled real price histories: one series per service, one cross-validation group per service. */
    List<PriceSeries> loadBundled() {
        Map<String, Builder> builders = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(BUNDLED_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header: service,category,date,price
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length < 4) {
                    continue;
                }
                builders.computeIfAbsent(parts[0], name -> new Builder("bundled-" + name, parts[1]))
                        .add(LocalDate.parse(parts[2]), Double.parseDouble(parts[3]));
            }
        } catch (IOException e) {
            log.warn("Bundled price history not found ({}); training on price_log data only", BUNDLED_RESOURCE);
        }
        return builders.values().stream().map(Builder::build).toList();
    }

    /** Collects points, dropping repeats so a series only contains actual price changes. */
    private static final class Builder {
        private final String group;
        private final String category;
        private final List<LocalDate> dates = new ArrayList<>();
        private final List<Double> prices = new ArrayList<>();

        Builder(String group, String category) {
            this.group = group;
            this.category = category;
        }

        void add(LocalDate date, double price) {
            if (price <= 0 || (!prices.isEmpty() && prices.get(prices.size() - 1) == price)) {
                return;
            }
            dates.add(date);
            prices.add(price);
        }

        PriceSeries build() {
            return new PriceSeries(group, category, dates, prices);
        }
    }
}
