package com.subshift.backend.ml;

import java.time.Instant;
import java.util.List;

/** What the price forecast model was trained on and how well it did in cross-validation. */
public record PriceForecastMetrics(
        Instant trainedAt,
        int realSeries,
        int syntheticSeries,
        int realTransitions,
        int syntheticTransitions,
        int folds,
        /** One row per model. Errors are for the NEXT price (rupees), growth (percentage points) and timing (months). */
        List<PriceModelEvaluator.Score> models,
        /** Ridge vs "no change" RMSE on real examples only. Null while there are too few real examples. */
        Double realRidgeRmse,
        Double realNoChangeRmse,
        String note) {}
