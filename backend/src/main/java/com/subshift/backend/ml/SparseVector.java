package com.subshift.backend.ml;

/** A sparse feature vector: parallel arrays of feature indices (ascending) and their values. */
public record SparseVector(int[] indices, double[] values) {

    public double dot(double[] weights) {
        double sum = 0;
        for (int i = 0; i < indices.length; i++) {
            sum += values[i] * weights[indices[i]];
        }
        return sum;
    }
}
