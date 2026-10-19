package com.subshift.backend.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** TF-IDF with sublinear term frequency and L2 normalisation. The vocabulary is learned from training data only. */
public final class TfIdfVectorizer {

    private final Map<String, Integer> vocabulary;
    private final String[] terms;
    private final double[] idf;

    private TfIdfVectorizer(Map<String, Integer> vocabulary, String[] terms, double[] idf) {
        this.vocabulary = vocabulary;
        this.terms = terms;
        this.idf = idf;
    }

    /** Keeps terms found in at least minDocs documents, and at most maxFeatures of the most common ones. */
    public static TfIdfVectorizer fit(List<List<String>> documents, int minDocs, int maxFeatures) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> document : documents) {
            Set<String> unique = new HashSet<>(document);
            for (String term : unique) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> kept = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            if (entry.getValue() >= minDocs) {
                kept.add(entry);
            }
        }
        // Most common first; ties broken alphabetically so training is fully deterministic.
        kept.sort((a, b) -> {
            int byCount = Integer.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        if (kept.size() > maxFeatures) {
            kept = kept.subList(0, maxFeatures);
        }

        Map<String, Integer> vocabulary = new HashMap<>();
        String[] terms = new String[kept.size()];
        double[] idf = new double[kept.size()];
        int n = documents.size();
        for (int i = 0; i < kept.size(); i++) {
            terms[i] = kept.get(i).getKey();
            vocabulary.put(terms[i], i);
            idf[i] = Math.log((1.0 + n) / (1.0 + kept.get(i).getValue())) + 1.0;
        }
        return new TfIdfVectorizer(vocabulary, terms, idf);
    }

    public SparseVector transform(List<String> document) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (String term : document) {
            Integer index = vocabulary.get(term);
            if (index != null) {
                counts.merge(index, 1, Integer::sum);
            }
        }
        int[] indices = new int[counts.size()];
        double[] values = new double[counts.size()];
        double norm = 0;
        int position = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            indices[position] = entry.getKey();
            values[position] = (1.0 + Math.log(entry.getValue())) * idf[entry.getKey()];
            norm += values[position] * values[position];
            position++;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < values.length; i++) {
                values[i] /= norm;
            }
        }
        return new SparseVector(indices, values);
    }

    public int size() {
        return terms.length;
    }

    public String termAt(int index) {
        return terms[index];
    }
}
