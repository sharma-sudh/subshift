package com.subshift.backend.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Stratified k-fold cross-validation. Each email is scored by a model that never saw it, and the
 * vectorizer is re-fitted inside every fold so the test emails cannot leak into the vocabulary.
 */
public final class ClassifierEvaluator {

    private static final int MIN_DOCS = 2;
    private static final int MAX_FEATURES = 5000;

    public record Result(String name, int truePositives, int falsePositives, int trueNegatives,
                         int falseNegatives, double[] outOfFoldProbabilities) {

        public double accuracy() {
            int total = truePositives + falsePositives + trueNegatives + falseNegatives;
            return total == 0 ? 0 : (double) (truePositives + trueNegatives) / total;
        }

        public double precision() {
            int predicted = truePositives + falsePositives;
            return predicted == 0 ? 0 : (double) truePositives / predicted;
        }

        public double recall() {
            int actual = truePositives + falseNegatives;
            return actual == 0 ? 0 : (double) truePositives / actual;
        }

        public double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0 : 2 * p * r / (p + r);
        }
    }

    private ClassifierEvaluator() {}

    public static List<Result> crossValidate(List<List<String>> documents, boolean[] positive, int folds,
                                             long seed, List<Supplier<BinaryModel>> factories) {
        int n = documents.size();
        int[] foldOf = assignFolds(positive, folds, seed);
        double[][] probabilities = new double[factories.size()][n];

        for (int fold = 0; fold < folds; fold++) {
            List<List<String>> trainDocs = new ArrayList<>();
            List<Boolean> trainLabels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (foldOf[i] != fold) {
                    trainDocs.add(documents.get(i));
                    trainLabels.add(positive[i]);
                }
            }
            TfIdfVectorizer vectorizer = TfIdfVectorizer.fit(trainDocs, MIN_DOCS, MAX_FEATURES);
            List<SparseVector> trainVectors = trainDocs.stream().map(vectorizer::transform).toList();
            boolean[] labels = new boolean[trainLabels.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = trainLabels.get(i);
            }
            for (int m = 0; m < factories.size(); m++) {
                BinaryModel model = factories.get(m).get();
                model.fit(trainVectors, labels, vectorizer.size());
                for (int i = 0; i < n; i++) {
                    if (foldOf[i] == fold) {
                        probabilities[m][i] = model.probability(vectorizer.transform(documents.get(i)));
                    }
                }
            }
        }

        List<Result> results = new ArrayList<>();
        for (int m = 0; m < factories.size(); m++) {
            int tp = 0, fp = 0, tn = 0, fn = 0;
            for (int i = 0; i < n; i++) {
                boolean predicted = probabilities[m][i] >= 0.5;
                if (predicted && positive[i]) tp++;
                else if (predicted) fp++;
                else if (positive[i]) fn++;
                else tn++;
            }
            results.add(new Result(factories.get(m).get().name(), tp, fp, tn, fn, probabilities[m]));
        }
        return results;
    }

    /** Spreads each class evenly over the folds, after a seeded shuffle. */
    private static int[] assignFolds(boolean[] positive, int folds, long seed) {
        List<Integer> positives = new ArrayList<>();
        List<Integer> negatives = new ArrayList<>();
        for (int i = 0; i < positive.length; i++) {
            (positive[i] ? positives : negatives).add(i);
        }
        Random random = new Random(seed);
        Collections.shuffle(positives, random);
        Collections.shuffle(negatives, random);
        int[] foldOf = new int[positive.length];
        for (int i = 0; i < positives.size(); i++) {
            foldOf[positives.get(i)] = i % folds;
        }
        for (int i = 0; i < negatives.size(); i++) {
            foldOf[negatives.get(i)] = i % folds;
        }
        return foldOf;
    }
}
