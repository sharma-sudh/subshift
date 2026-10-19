package com.subshift.backend.ml;

import com.subshift.backend.ml.ClassifierMetrics.ModelScore;
import com.subshift.backend.training.ClassifierTrainingEmail;
import com.subshift.backend.training.ClassifierTrainingEmailRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decides whether an email looks like a subscription renewal, before the (rate limited) Gemini call.
 *
 * It is trained from the classifier_training_emails table, compares Naive Bayes with logistic
 * regression using cross-validation, and serves the better one. It only ever filters out an email
 * when it is very sure the email is NOT a renewal: wrongly skipping a renewal is costly, while a
 * wasted Gemini call is cheap.
 */
@Slf4j
@Service
public class EmailClassifierService {

    private static final int MIN_EMAILS = 20;
    private static final int MIN_PER_LABEL = 5;
    private static final int FOLDS = 5;
    private static final long SEED = 42;
    private static final int MIN_DOCS = 2;
    private static final int MAX_FEATURES = 5000;
    private static final int TOP_TERMS = 10;
    /** The automatic threshold is half the lowest score any real renewal got in cross-validation, capped here. */
    private static final double AUTO_THRESHOLD_CAP = 0.07;
    private static final double AUTO_THRESHOLD_MARGIN = 0.5;

    public record Prediction(double renewalProbability, boolean wouldSkip, double threshold, String model) {}

    private record Trained(TfIdfVectorizer vectorizer, BinaryModel model, double threshold, ClassifierMetrics metrics) {}

    private final ClassifierTrainingEmailRepository repository;
    private final double configuredThreshold;
    private volatile Trained trained;

    /** ml.classifier.skip-below: a value above 0 fixes the threshold; otherwise it is derived when training. */
    public EmailClassifierService(ClassifierTrainingEmailRepository repository,
                                  @Value("${ml.classifier.skip-below:-1}") double configuredThreshold) {
        this.repository = repository;
        this.configuredThreshold = configuredThreshold;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void trainOnStartup() {
        try {
            ClassifierMetrics metrics = train();
            log.info("Email classifier trained on {} emails: {} serves, skip threshold {}",
                    metrics.trainingEmails(), metrics.servedModel(), metrics.skipThreshold());
        } catch (ResponseStatusException e) {
            log.info("Email classifier not trained at startup: {}", e.getReason());
        } catch (Exception e) {
            log.warn("Email classifier training failed at startup", e);
        }
    }

    public synchronized ClassifierMetrics train() {
        List<ClassifierTrainingEmail> emails = repository.findAll();
        long renewals = emails.stream().filter(e -> "RENEWAL".equals(e.getLabel())).count();
        long nonRenewals = emails.size() - renewals;
        if (emails.size() < MIN_EMAILS || renewals < MIN_PER_LABEL || nonRenewals < MIN_PER_LABEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Need at least " + MIN_EMAILS + " labeled emails with at least " + MIN_PER_LABEL
                            + " of each label (have " + renewals + " RENEWAL, " + nonRenewals + " NOT_RENEWAL)");
        }

        List<List<String>> documents = new ArrayList<>();
        boolean[] positive = new boolean[emails.size()];
        for (int i = 0; i < emails.size(); i++) {
            documents.add(TextPreprocessor.terms(emails.get(i).getEmailText()));
            positive[i] = "RENEWAL".equals(emails.get(i).getLabel());
        }

        List<Supplier<BinaryModel>> factories = List.of(NaiveBayesModel::new, LogisticRegressionModel::new);
        List<ClassifierEvaluator.Result> results =
                ClassifierEvaluator.crossValidate(documents, positive, FOLDS, SEED, factories);
        int served = results.get(1).f1() >= results.get(0).f1() ? 1 : 0; // a tie goes to logistic regression
        ClassifierEvaluator.Result servedResult = results.get(served);

        double[] outOfFold = servedResult.outOfFoldProbabilities();
        double lowestRenewal = 1.0;
        for (int i = 0; i < positive.length; i++) {
            if (positive[i]) {
                lowestRenewal = Math.min(lowestRenewal, outOfFold[i]);
            }
        }
        boolean fromConfig = configuredThreshold > 0;
        double threshold = fromConfig ? configuredThreshold
                : Math.min(AUTO_THRESHOLD_CAP, AUTO_THRESHOLD_MARGIN * lowestRenewal);
        int renewalsBelow = 0;
        int nonRenewalsBelow = 0;
        for (int i = 0; i < positive.length; i++) {
            if (outOfFold[i] < threshold) {
                if (positive[i]) {
                    renewalsBelow++;
                } else {
                    nonRenewalsBelow++;
                }
            }
        }

        TfIdfVectorizer vectorizer = TfIdfVectorizer.fit(documents, MIN_DOCS, MAX_FEATURES);
        List<SparseVector> vectors = documents.stream().map(vectorizer::transform).toList();
        BinaryModel model = factories.get(served).get();
        model.fit(vectors, positive, vectorizer.size());

        List<ModelScore> scores = results.stream().map(EmailClassifierService::toScore).toList();
        ClassifierMetrics metrics = new ClassifierMetrics(Instant.now(), emails.size(), (int) renewals,
                (int) nonRenewals, vectorizer.size(), FOLDS, servedResult.name(), round(threshold), fromConfig,
                renewalsBelow, nonRenewalsBelow, scores,
                topTerms(vectorizer, model.weights(), true), topTerms(vectorizer, model.weights(), false));
        trained = new Trained(vectorizer, model, threshold, metrics);
        return metrics;
    }

    public boolean isReady() {
        return trained != null;
    }

    public Optional<ClassifierMetrics> metrics() {
        Trained current = trained;
        return current == null ? Optional.empty() : Optional.of(current.metrics());
    }

    /** Probability that the text is a renewal, or null while no model has been trained. */
    public Double score(String text) {
        Trained current = trained;
        if (current == null) {
            return null;
        }
        return current.model().probability(current.vectorizer().transform(TextPreprocessor.terms(text)));
    }

    /** True only when the classifier is confident the email is not a renewal. */
    public boolean shouldSkip(Double score) {
        Trained current = trained;
        return current != null && score != null && score < current.threshold();
    }

    public Prediction predict(String text) {
        Trained current = trained;
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The classifier has not been trained yet");
        }
        double probability = score(text);
        return new Prediction(round(probability), probability < current.threshold(), round(current.threshold()),
                current.metrics().servedModel());
    }

    private static ModelScore toScore(ClassifierEvaluator.Result r) {
        return new ModelScore(r.name(), round(r.accuracy()), round(r.precision()), round(r.recall()), round(r.f1()),
                r.truePositives(), r.falsePositives(), r.trueNegatives(), r.falseNegatives());
    }

    private static List<String> topTerms(TfIdfVectorizer vectorizer, double[] weights, boolean positive) {
        Integer[] order = new Integer[weights.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Comparator<Integer> byWeight = Comparator.comparingDouble(i -> weights[i]);
        Arrays.sort(order, positive ? byWeight.reversed() : byWeight);
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_TERMS, order.length); i++) {
            terms.add(vectorizer.termAt(order[i]));
        }
        return terms;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
