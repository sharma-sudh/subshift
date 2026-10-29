package com.subshift.backend.ml;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Trains both classifiers on a tiny hand-written corpus. Plain unit test: no Spring, no database. */
class EmailClassifierModelsTest {

    private static final List<String> RENEWALS = List.of(
            "Your subscription renews monthly and your plan was renewed",
            "Subscription renewal notice: your membership will renew automatically",
            "Your monthly plan has been renewed, manage your subscription",
            "Recurring subscription payment received for your plan renewal");
    private static final List<String> OTHERS = List.of(
            "Your order has shipped and will arrive on Friday",
            "Meeting agenda for the project review next week",
            "Your one time purchase receipt for a book",
            "Newsletter: top travel destinations and cooking tips");

    private List<SparseVector> features;
    private boolean[] labels;
    private TfIdfVectorizer vectorizer;

    private void prepare() {
        List<String> all = new ArrayList<>(RENEWALS);
        all.addAll(OTHERS);
        List<List<String>> documents = all.stream().map(TextPreprocessor::terms).toList();
        vectorizer = TfIdfVectorizer.fit(documents, 1, 500);
        features = documents.stream().map(vectorizer::transform).toList();
        labels = new boolean[all.size()];
        for (int i = 0; i < RENEWALS.size(); i++) labels[i] = true;
    }

    private SparseVector vectorOf(String text) {
        return vectorizer.transform(TextPreprocessor.terms(text));
    }

    private void checkSeparates(BinaryModel model) {
        prepare();
        model.fit(features, labels, vectorizer.size());
        double renewal = model.probability(vectorOf("Your subscription plan renewal is processed monthly"));
        double other = model.probability(vectorOf("Your package has shipped, delivery on Friday"));
        assertTrue(renewal > 0.5, model.name() + " should call this a renewal: " + renewal);
        assertTrue(other < 0.5, model.name() + " should not call this a renewal: " + other);
        assertTrue(renewal > other);
    }

    @Test
    void logisticRegressionSeparatesRenewalsFromOtherMail() {
        checkSeparates(new LogisticRegressionModel());
    }

    @Test
    void naiveBayesSeparatesRenewalsFromOtherMail() {
        checkSeparates(new NaiveBayesModel());
    }

    @Test
    void probabilitiesStayBetweenZeroAndOne() {
        prepare();
        LogisticRegressionModel model = new LogisticRegressionModel();
        model.fit(features, labels, vectorizer.size());
        for (SparseVector v : features) {
            double p = model.probability(v);
            assertTrue(p >= 0.0 && p <= 1.0);
        }
    }
}
