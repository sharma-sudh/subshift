package com.subshift.backend.ml;

import java.util.List;

/** A linear binary classifier. The positive class is RENEWAL. */
public interface BinaryModel {

    String name();

    void fit(List<SparseVector> features, boolean[] positive, int vocabularySize);

    /** Probability that the document is a renewal, between 0 and 1. */
    double probability(SparseVector features);

    /** One weight per feature: positive pushes towards RENEWAL, negative towards NOT_RENEWAL. */
    double[] weights();
}
