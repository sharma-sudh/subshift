package com.subshift.backend.ml;

import java.util.List;

/** Two ridge models: how much the NEXT price change will be (log growth), and how many months until it happens. */
public final class PriceForecastModel {

    public static final double LAMBDA = 3.0;
    private static final double MIN_GROWTH = -0.05;
    private static final double MAX_GROWTH = 0.60;
    private static final double MIN_MONTHS = 3;
    private static final double MAX_MONTHS = 60;

    public record Prediction(double growth, double months) {}

    private final RidgeRegression growthModel = new RidgeRegression(LAMBDA);
    private final RidgeRegression monthsModel = new RidgeRegression(LAMBDA);

    public void fit(List<PriceFeatures.Transition> transitions) {
        double[][] x = new double[transitions.size()][];
        double[] growth = new double[transitions.size()];
        double[] months = new double[transitions.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = transitions.get(i).features();
            growth[i] = transitions.get(i).growth();
            months[i] = transitions.get(i).months();
        }
        growthModel.fit(x, growth);
        monthsModel.fit(x, months);
    }

    public Prediction predict(double[] features) {
        double growth = Math.min(MAX_GROWTH, Math.max(MIN_GROWTH, growthModel.predict(features)));
        double months = Math.min(MAX_MONTHS, Math.max(MIN_MONTHS, monthsModel.predict(features)));
        return new Prediction(growth, months);
    }
}
