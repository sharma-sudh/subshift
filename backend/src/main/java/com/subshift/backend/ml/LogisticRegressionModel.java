package com.subshift.backend.ml;

import java.util.List;

/** Logistic regression with L2 regularisation, trained by full-batch gradient descent (deterministic). */
public final class LogisticRegressionModel implements BinaryModel {

    private static final int DEFAULT_ITERATIONS = 1000;
    private static final double DEFAULT_LEARNING_RATE = 4.0;
    private static final double DEFAULT_L2 = 3e-4;

    private final int iterations;
    private final double learningRate;
    private final double l2;
    private double[] weights = new double[0];
    private double bias;

    public LogisticRegressionModel() {
        this(DEFAULT_ITERATIONS, DEFAULT_LEARNING_RATE, DEFAULT_L2);
    }

    public LogisticRegressionModel(int iterations, double learningRate, double l2) {
        this.iterations = iterations;
        this.learningRate = learningRate;
        this.l2 = l2;
    }

    @Override
    public String name() {
        return "Logistic Regression";
    }

    @Override
    public void fit(List<SparseVector> features, boolean[] positive, int vocabularySize) {
        weights = new double[vocabularySize];
        bias = 0;
        int n = features.size();
        for (int iteration = 0; iteration < iterations; iteration++) {
            double[] gradient = new double[vocabularySize];
            double biasGradient = 0;
            for (int i = 0; i < n; i++) {
                SparseVector vector = features.get(i);
                double error = sigmoid(bias + vector.dot(weights)) - (positive[i] ? 1.0 : 0.0);
                for (int k = 0; k < vector.indices().length; k++) {
                    gradient[vector.indices()[k]] += error * vector.values()[k];
                }
                biasGradient += error;
            }
            for (int j = 0; j < vocabularySize; j++) {
                weights[j] -= learningRate * (gradient[j] / n + l2 * weights[j]);
            }
            bias -= learningRate * biasGradient / n;
        }
    }

    @Override
    public double probability(SparseVector features) {
        return sigmoid(bias + features.dot(weights));
    }

    @Override
    public double[] weights() {
        return weights;
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }
}
