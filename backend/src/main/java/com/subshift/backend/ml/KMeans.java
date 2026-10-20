package com.subshift.backend.ml;

import java.util.Random;

/**
 * K-means clustering (Lloyd's algorithm with k-means++ seeding and several restarts) plus the silhouette score.
 * Pure Java, no dependencies. Seeded, so the same input always gives the same clusters.
 */
public final class KMeans {

    private static final int MAX_ITERATIONS = 100;

    private KMeans() {
    }

    /** assignment[i] is the cluster of point i; inertia is the sum of squared distances to the centroids. */
    public record Result(int[] assignment, double[][] centroids, double inertia) {
    }

    /** Runs k-means {@code restarts} times and keeps the run with the lowest inertia. */
    public static Result fit(double[][] points, int k, long seed, int restarts) {
        if (k < 1 || k > points.length) {
            throw new IllegalArgumentException("k must be between 1 and the number of points");
        }
        Random random = new Random(seed);
        Result best = null;
        for (int r = 0; r < restarts; r++) {
            Result result = runOnce(points, k, random);
            if (best == null || result.inertia() < best.inertia()) {
                best = result;
            }
        }
        return best;
    }

    private static Result runOnce(double[][] points, int k, Random random) {
        int n = points.length;
        double[][] centroids = seed(points, k, random);
        int[] assignment = new int[n];
        java.util.Arrays.fill(assignment, -1);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int nearest = nearest(points[i], centroids);
                if (nearest != assignment[i]) {
                    assignment[i] = nearest;
                    changed = true;
                }
            }
            recompute(points, assignment, centroids);
            if (!changed) {
                break;
            }
        }
        double inertia = 0;
        for (int i = 0; i < n; i++) {
            inertia += squaredDistance(points[i], centroids[assignment[i]]);
        }
        return new Result(assignment, centroids, inertia);
    }

    /** k-means++: each new centroid is picked with probability proportional to its squared distance from the nearest one. */
    private static double[][] seed(double[][] points, int k, Random random) {
        int n = points.length;
        double[][] centroids = new double[k][];
        centroids[0] = points[random.nextInt(n)].clone();
        double[] distance = new double[n];
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int i = 0; i < n; i++) {
                double d = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    d = Math.min(d, squaredDistance(points[i], centroids[j]));
                }
                distance[i] = d;
                total += d;
            }
            int chosen = n - 1;
            if (total == 0) {
                chosen = random.nextInt(n); // all points coincide with a centroid already
            } else {
                double target = random.nextDouble() * total;
                double running = 0;
                for (int i = 0; i < n; i++) {
                    running += distance[i];
                    if (running >= target) {
                        chosen = i;
                        break;
                    }
                }
            }
            centroids[c] = points[chosen].clone();
        }
        return centroids;
    }

    private static void recompute(double[][] points, int[] assignment, double[][] centroids) {
        int k = centroids.length;
        int dims = points[0].length;
        double[][] sums = new double[k][dims];
        int[] counts = new int[k];
        for (int i = 0; i < points.length; i++) {
            counts[assignment[i]]++;
            for (int d = 0; d < dims; d++) {
                sums[assignment[i]][d] += points[i][d];
            }
        }
        for (int c = 0; c < k; c++) {
            if (counts[c] == 0) {
                // Empty cluster: move its centroid to the point that is worst served right now.
                int worst = 0;
                double worstDistance = -1;
                for (int i = 0; i < points.length; i++) {
                    double d = squaredDistance(points[i], centroids[assignment[i]]);
                    if (d > worstDistance) {
                        worstDistance = d;
                        worst = i;
                    }
                }
                centroids[c] = points[worst].clone();
            } else {
                for (int d = 0; d < dims; d++) {
                    centroids[c][d] = sums[c][d] / counts[c];
                }
            }
        }
    }

    private static int nearest(double[] point, double[][] centroids) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int c = 0; c < centroids.length; c++) {
            double d = squaredDistance(point, centroids[c]);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    static double squaredDistance(double[] a, double[] b) {
        double sum = 0;
        for (int d = 0; d < a.length; d++) {
            double diff = a[d] - b[d];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Mean silhouette score, from -1 to 1 (higher is better separated). For each point: a is its mean distance to
     * the other points of its own cluster, b the smallest mean distance to another cluster, s = (b - a) / max(a, b).
     * A point alone in its cluster scores 0.
     */
    public static double silhouette(double[][] points, int[] assignment, int k) {
        int n = points.length;
        double total = 0;
        for (int i = 0; i < n; i++) {
            double[] sum = new double[k];
            int[] count = new int[k];
            for (int j = 0; j < n; j++) {
                if (j == i) {
                    continue;
                }
                sum[assignment[j]] += Math.sqrt(squaredDistance(points[i], points[j]));
                count[assignment[j]]++;
            }
            int own = assignment[i];
            if (count[own] == 0) {
                continue; // singleton cluster, score 0
            }
            double a = sum[own] / count[own];
            double b = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (c != own && count[c] > 0) {
                    b = Math.min(b, sum[c] / count[c]);
                }
            }
            double denominator = Math.max(a, b);
            total += denominator == 0 ? 0 : (b - a) / denominator;
        }
        return total / n;
    }
}
