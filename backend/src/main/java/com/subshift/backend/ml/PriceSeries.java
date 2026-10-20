package com.subshift.backend.ml;

import java.time.LocalDate;
import java.util.List;

/**
 * The price history of one subscription: each point is the date a NEW price took effect.
 * group is used to keep related series in the same cross-validation fold (all subscriptions to the same
 * service share a group), so the model is always tested on services it has not seen.
 */
public record PriceSeries(String group, String category, boolean synthetic,
                          List<LocalDate> dates, List<Double> prices) {}
