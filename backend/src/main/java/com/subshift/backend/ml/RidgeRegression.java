package com.subshift.backend.ml;

/** Ridge (L2-regularised) linear regression, solved exactly. Features are standardised internally. */
public final class RidgeRegression {

    private final double lambda;
    private double[] mean = new double[0];
    private double[] scale = new double[0];
    private double[] weights = new double[0];
    private double intercept;

    public RidgeRegression(double lambda) {
        this.lambda = lambda;
    }

    public void fit(double[][] x, double[] y) {
        int n = x.length;
        int d = x[0].length;
        mean = new double[d];
        scale = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (double[] row : x) sum += row[j];
            mean[j] = sum / n;
            double variance = 0;
            for (double[] row : x) variance += (row[j] - mean[j]) * (row[j] - mean[j]);
            scale[j] = Math.sqrt(variance / n);
            if (scale[j] < 1e-9) scale[j] = 1.0; // constant feature: leave it at zero after centring
        }
        double yMean = 0;
        for (double v : y) yMean += v;
        yMean /= n;

        double[][] a = new double[d][d + 1]; // augmented matrix [XtX + lambda*I | Xty]
        for (int r = 0; r < n; r++) {
            double[] z = standardise(x[r]);
            for (int i = 0; i < d; i++) {
                for (int j = 0; j < d; j++) a[i][j] += z[i] * z[j];
                a[i][d] += z[i] * (y[r] - yMean);
            }
        }
        for (int i = 0; i < d; i++) a[i][i] += lambda;
        weights = solve(a, d);
        intercept = yMean;
    }

    public double predict(double[] x) {
        double[] z = standardise(x);
        double sum = intercept;
        for (int j = 0; j < z.length; j++) sum += weights[j] * z[j];
        return sum;
    }

    private double[] standardise(double[] x) {
        double[] z = new double[x.length];
        for (int j = 0; j < x.length; j++) z[j] = (x[j] - mean[j]) / scale[j];
        return z;
    }

    /** Gaussian elimination with partial pivoting on the augmented matrix. */
    private static double[] solve(double[][] a, int d) {
        for (int col = 0; col < d; col++) {
            int pivot = col;
            for (int r = col + 1; r < d; r++) if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            double[] swap = a[col]; a[col] = a[pivot]; a[pivot] = swap;
            double p = a[col][col];
            for (int c = col; c <= d; c++) a[col][c] /= p;
            for (int r = 0; r < d; r++) {
                if (r == col) continue;
                double factor = a[r][col];
                for (int c = col; c <= d; c++) a[r][c] -= factor * a[col][c];
            }
        }
        double[] result = new double[d];
        for (int i = 0; i < d; i++) result[i] = a[i][d];
        return result;
    }
}
