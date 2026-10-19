package com.subshift.backend.ml;

import java.util.List;

/**
 * Multinomial Naive Bayes over TF-IDF weights. For two classes it reduces to a linear model:
 * log-odds = log prior ratio + sum(feature value * log likelihood ratio).
 */
public final class NaiveBayesModel implements BinaryModel {

    private static final double ALPHA = 0.1; // additive smoothing

    private double[] weights = new double[0];
    private double bias;

    @Override
    public String name() {
        return "Naive Bayes";
    }

    @Override
    public void fit(List<SparseVector> features, boolean[] positive, int vocabularySize) {
        double[] positiveMass = new double[vocabularySize];
        double[] negativeMass = new double[vocabularySize];
        double positiveTotal = 0;
        double negativeTotal = 0;
        int positiveDocs = 0;
        for (int i = 0; i < features.size(); i++) {
            SparseVector vector = features.get(i);
            for (int k = 0; k < vector.indices().length; k++) {
                if (positive[i]) {
                    positiveMass[vector.indices()[k]] += vector.values()[k];
                    positiveTotal += vector.values()[k];
                } else {
                    negativeMass[vector.indices()[k]] += vector.values()[k];
                    negativeTotal += vector.values()[k];
                }
            }
            if (positive[i]) {
                positiveDocs++;
            }
        }
        int negativeDocs = features.size() - positiveDocs;
        weights = new double[vocabularySize];
        for (int j = 0; j < vocabularySize; j++) {
            double logPositive = Math.log((positiveMass[j] + ALPHA) / (positiveTotal + ALPHA * vocabularySize));
            double logNegative = Math.log((negativeMass[j] + ALPHA) / (negativeTotal + ALPHA * vocabularySize));
            weights[j] = logPositive - logNegative;
        }
        bias = Math.log((double) positiveDocs / negativeDocs);
    }

    @Override
    public double probability(SparseVector features) {
        return 1.0 / (1.0 + Math.exp(-(bias + features.dot(weights))));
    }

    @Override
    public double[] weights() {
        return weights;
    }
}
