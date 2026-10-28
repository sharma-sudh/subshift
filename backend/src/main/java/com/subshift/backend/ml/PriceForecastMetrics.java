package com.subshift.backend.ml;

import java.time.Instant;
import java.util.List;

/** What the price forecast model was trained on and how well it did in cross-validation. */
public record PriceForecastMetrics(
        Instant trainedAt,
        int series,
        int transitions,
        int folds,
        /** One row per model. Errors are for the NEXT price (rupees), growth (percentage points) and timing (months). */
        List<PriceModelEvaluator.Score> models,
        /** Plain-language comparison of Ridge against the "Average increase" baseline. */
        String note) {}
