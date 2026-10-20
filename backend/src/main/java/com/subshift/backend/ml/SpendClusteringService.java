package com.subshift.backend.ml;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Groups one user's subscriptions into spend tiers (for example Low, Medium, High) with k-means on the log of the
 * current price. The number of tiers (2 to 4) is the one with the best silhouette score. Log price is used so that
 * a 99 vs 199 difference counts about as much as 499 vs 999.
 */
@Service
public class SpendClusteringService {

    static final long SEED = 42;
    static final int RESTARTS = 10;
    static final int MIN_POINTS = 3;
    static final int MAX_K = 4;

    public record PricedItem(String name, String category, BigDecimal price) {
    }

    public record Candidate(int k, double silhouette) {
    }

    public record Tier(String label, BigDecimal minPrice, BigDecimal maxPrice, int subscriptionCount,
                       BigDecimal totalSpend, List<String> services) {
    }

    /**
     * When {@code available} is false only {@code note} is filled in. {@code candidates} lists the silhouette of every
     * k that was tried; {@code quality} is a plain-words reading of the chosen silhouette.
     */
    public record SpendTiers(boolean available, String note, Integer k, Double silhouette, Double inertia,
                             String quality, List<Candidate> candidates, List<Tier> tiers) {
    }

    public SpendTiers cluster(List<PricedItem> allItems) {
        List<PricedItem> items = allItems.stream()
                .filter(i -> i.price() != null && i.price().signum() > 0)
                .toList();
        long distinctPrices = items.stream().map(i -> i.price().stripTrailingZeros()).distinct().count();
        if (items.size() < MIN_POINTS || distinctPrices < MIN_POINTS) {
            return unavailable("Need at least " + MIN_POINTS + " subscriptions with different prices to find spend tiers.");
        }

        double[][] points = new double[items.size()][1];
        for (int i = 0; i < items.size(); i++) {
            points[i][0] = Math.log(items.get(i).price().doubleValue());
        }

        int maxK = (int) Math.min(MAX_K, distinctPrices - 1);
        KMeans.Result bestResult = null;
        double bestScore = -2;
        int bestK = 0;
        List<Candidate> candidates = new ArrayList<>();
        for (int k = 2; k <= maxK; k++) {
            KMeans.Result result = KMeans.fit(points, k, SEED, RESTARTS);
            double score = KMeans.silhouette(points, result.assignment(), k);
            candidates.add(new Candidate(k, round(score)));
            if (score > bestScore) {
                bestScore = score;
                bestResult = result;
                bestK = k;
            }
        }

        // Order the clusters from cheapest to most expensive centroid so labels are stable.
        Integer[] order = new Integer[bestK];
        for (int c = 0; c < bestK; c++) {
            order[c] = c;
        }
        final double[][] centroids = bestResult.centroids();
        java.util.Arrays.sort(order, Comparator.comparingDouble(c -> centroids[c][0]));

        String[] labels = labelsFor(bestK);
        List<Tier> tiers = new ArrayList<>();
        for (int rank = 0; rank < bestK; rank++) {
            int cluster = order[rank];
            List<PricedItem> members = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (bestResult.assignment()[i] == cluster) {
                    members.add(items.get(i));
                }
            }
            BigDecimal min = members.stream().map(PricedItem::price).min(Comparator.naturalOrder()).orElseThrow();
            BigDecimal max = members.stream().map(PricedItem::price).max(Comparator.naturalOrder()).orElseThrow();
            BigDecimal total = members.stream().map(PricedItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<String> names = new ArrayList<>(new TreeSet<>(members.stream().map(PricedItem::name).toList()));
            tiers.add(new Tier(labels[rank], min, max, members.size(), total, names));
        }

        return new SpendTiers(true, null, bestK, round(bestScore), round(bestResult.inertia()),
                quality(bestScore), candidates, tiers);
    }

    private static SpendTiers unavailable(String note) {
        return new SpendTiers(false, note, null, null, null, null, List.of(), List.of());
    }

    private static String[] labelsFor(int k) {
        return switch (k) {
            case 2 -> new String[]{"Low", "High"};
            case 3 -> new String[]{"Low", "Medium", "High"};
            default -> new String[]{"Low", "Medium", "High", "Very high"};
        };
    }

    /** Common reading of the silhouette score (Kaufman and Rousseeuw). */
    static String quality(double silhouette) {
        if (silhouette > 0.7) {
            return "strong";
        }
        if (silhouette > 0.5) {
            return "reasonable";
        }
        if (silhouette > 0.25) {
            return "weak";
        }
        return "no real structure";
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
