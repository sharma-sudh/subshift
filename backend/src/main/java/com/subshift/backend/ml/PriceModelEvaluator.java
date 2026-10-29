package com.subshift.backend.ml;

import com.subshift.backend.ml.PriceFeatures.Transition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Grouped k-fold cross-validation: every service's history sits in exactly one fold, so each prediction
 * comes from a model that never saw that service. The number of folds is capped at the number of services, so
 * with little data this becomes leave-one-service-out. Errors are reported on the price scale (rupees), as
 * percentage points of growth, and (for timing) in months.
 */
public final class PriceModelEvaluator {

    public record Score(String name, double rmseRupees, double maeRupees, double rmseGrowthPoints, double maeMonths) {}

    public record Evaluation(List<Score> scores, int transitions, int groups, int folds) {}

    private static final String[] NAMES = {"Ridge regression", "No change", "Repeat last increase", "Average increase"};

    private PriceModelEvaluator() {}

    public static Evaluation crossValidate(List<Transition> all, int folds, long seed) {
        List<String> groups = new ArrayList<>(new LinkedHashSet<>(all.stream().map(t -> t.series().group()).toList()));
        Collections.shuffle(groups, new Random(seed));
        folds = Math.max(1, Math.min(folds, groups.size()));
        Map<String, Integer> foldOf = new HashMap<>();
        for (int i = 0; i < groups.size(); i++) foldOf.put(groups.get(i), i % folds);

        int n = all.size();
        double[][] predictedGrowth = new double[NAMES.length][n];
        double[][] predictedMonths = new double[NAMES.length][n];

        for (int fold = 0; fold < folds; fold++) {
            List<Transition> train = new ArrayList<>();
            for (Transition t : all) if (foldOf.get(t.series().group()) != fold) train.add(t);
            if (train.isEmpty()) continue;
            PriceForecastModel model = new PriceForecastModel();
            model.fit(train);
            double meanGrowth = train.stream().mapToDouble(Transition::growth).average().orElse(0);
            double meanGap = train.stream().mapToDouble(Transition::months).average().orElse(12);
            for (int i = 0; i < n; i++) {
                Transition t = all.get(i);
                if (foldOf.get(t.series().group()) != fold) continue;
                PriceForecastModel.Prediction p = model.predict(t.features());
                predictedGrowth[0][i] = p.growth();           predictedMonths[0][i] = p.months();
                predictedGrowth[1][i] = 0.0;                  predictedMonths[1][i] = meanGap;
                predictedGrowth[2][i] = t.lastGrowth();       predictedMonths[2][i] = t.lastGapMonths();
                predictedGrowth[3][i] = meanGrowth;           predictedMonths[3][i] = meanGap;
            }
        }

        List<Score> scores = new ArrayList<>();
        for (int m = 0; m < NAMES.length; m++) {
            double sq = 0, abs = 0, sqGrowth = 0, absMonths = 0;
            for (int i = 0; i < n; i++) {
                Transition t = all.get(i);
                double error = t.price() * Math.exp(predictedGrowth[m][i]) - t.nextPrice();
                sq += error * error;
                abs += Math.abs(error);
                double growthError = predictedGrowth[m][i] - t.growth();
                sqGrowth += growthError * growthError;
                absMonths += Math.abs(predictedMonths[m][i] - t.months());
            }
            scores.add(new Score(NAMES[m], Math.sqrt(sq / n), abs / n, 100.0 * Math.sqrt(sqGrowth / n), absMonths / n));
        }

        return new Evaluation(scores, n, groups.size(), folds);
    }
}
