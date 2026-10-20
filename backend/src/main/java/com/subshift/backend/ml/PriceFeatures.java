package com.subshift.backend.ml;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Turns a price history into the numbers the forecast model learns from. */
public final class PriceFeatures {

    public static final List<String> CATEGORIES = List.of("Entertainment", "Productivity", "Fitness", "Utilities", "Other");

    /** One training example: the history up to price index, and what the next change turned out to be. */
    public record Transition(PriceSeries series, double[] features, double price, double nextPrice,
                             double lastGrowth, double lastGapMonths, double growth, double months) {}

    private PriceFeatures() {}

    public static double monthsBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 30.4375;
    }

    /**
     * Features describing the history through price index j (needs j >= 1, so at least two prices):
     * log of the current price, the last log-growth, the average log-growth so far, the last gap in months,
     * the average gap, how many changes were seen, and the category.
     */
    public static double[] features(PriceSeries s, int j) {
        double sumGrowth = 0;
        double sumGap = 0;
        for (int i = 1; i <= j; i++) {
            sumGrowth += Math.log(s.prices().get(i) / s.prices().get(i - 1));
            sumGap += monthsBetween(s.dates().get(i - 1), s.dates().get(i));
        }
        double[] f = new double[5 + 1 + CATEGORIES.size()];
        f[0] = Math.log(s.prices().get(j));
        f[1] = Math.log(s.prices().get(j) / s.prices().get(j - 1));
        f[2] = sumGrowth / j;
        f[3] = monthsBetween(s.dates().get(j - 1), s.dates().get(j));
        f[4] = sumGap / j;
        f[5] = Math.min(j, 8);
        int category = CATEGORIES.indexOf(s.category());
        f[6 + (category < 0 ? CATEGORIES.indexOf("Other") : category)] = 1.0;
        return f;
    }

    /** Every point that has both a previous change and a following change becomes one training example. */
    public static List<Transition> transitions(List<PriceSeries> allSeries) {
        List<Transition> result = new ArrayList<>();
        for (PriceSeries s : allSeries) {
            for (int j = 1; j + 1 < s.prices().size(); j++) {
                double price = s.prices().get(j);
                double next = s.prices().get(j + 1);
                result.add(new Transition(s, features(s, j), price, next,
                        Math.log(price / s.prices().get(j - 1)),
                        monthsBetween(s.dates().get(j - 1), s.dates().get(j)),
                        Math.log(next / price),
                        monthsBetween(s.dates().get(j), s.dates().get(j + 1))));
            }
        }
        return result;
    }
}
