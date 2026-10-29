package com.subshift.backend.ml;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Turns a price history into the numbers the forecast model learns from. */
public final class PriceFeatures {

    /** Number of features: last growth, average growth, last gap, average gap. */
    public static final int FEATURE_COUNT = 4;

    /** One training example: the history up to price index, and what the next change turned out to be. */
    public record Transition(PriceSeries series, double[] features, double price, double nextPrice,
                             double lastGrowth, double lastGapMonths, double growth, double months) {}

    private PriceFeatures() {}

    public static double monthsBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 30.4375;
    }

    /**
     * Features describing the history through price index j (needs j >= 1, so at least two prices), using only
     * the history's own shape so they do not depend on the currency or on a service's category:
     * the last log-growth, the average log-growth so far, the last gap in months and the average gap.
     * (Price level, number of changes and category were dropped: with ~30 real examples they only added noise.)
     */
    public static double[] features(PriceSeries s, int j) {
        double sumGrowth = 0;
        double sumGap = 0;
        for (int i = 1; i <= j; i++) {
            sumGrowth += Math.log(s.prices().get(i) / s.prices().get(i - 1));
            sumGap += monthsBetween(s.dates().get(i - 1), s.dates().get(i));
        }
        double[] f = new double[FEATURE_COUNT];
        f[0] = Math.log(s.prices().get(j) / s.prices().get(j - 1));
        f[1] = sumGrowth / j;
        f[2] = monthsBetween(s.dates().get(j - 1), s.dates().get(j));
        f[3] = sumGap / j;
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
