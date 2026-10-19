package com.subshift.backend.ml;

import java.time.Instant;
import java.util.List;

/** Everything worth reporting about a trained classifier. Scores come from cross-validation, not from the training data. */
public record ClassifierMetrics(
        Instant trainedAt,
        int trainingEmails,
        int renewals,
        int nonRenewals,
        int vocabularySize,
        int folds,
        String servedModel,
        /** Emails scoring below this renewal probability are skipped before Gemini is called. */
        double skipThreshold,
        boolean thresholdFromConfig,
        /** Cross-validation check of the threshold: real renewals that would have been skipped (should be 0). */
        int renewalsBelowThreshold,
        /** Cross-validation check of the threshold: non-renewals that would have been skipped (Gemini calls saved). */
        int nonRenewalsBelowThreshold,
        List<ModelScore> models,
        List<String> topRenewalTerms,
        List<String> topNotRenewalTerms) {

    public record ModelScore(String name, double accuracy, double precision, double recall, double f1,
                             int truePositives, int falsePositives, int trueNegatives, int falseNegatives) {}
}
