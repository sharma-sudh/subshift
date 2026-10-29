package com.subshift.backend.ml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests: no Spring, no database. */
class RidgeRegressionTest {

    @Test
    void learnsALinearRelationship() {
        // y = 2x + 1
        double[][] x = {{1}, {2}, {3}, {4}, {5}, {6}};
        double[] y = {3, 5, 7, 9, 11, 13};
        RidgeRegression model = new RidgeRegression(1e-6);
        model.fit(x, y);
        assertEquals(21.0, model.predict(new double[]{10}), 0.01);
        assertEquals(1.0, model.predict(new double[]{0}), 0.01);
    }

    @Test
    void constantFeatureDoesNotBreakTheFit() {
        double[][] x = {{1, 5}, {2, 5}, {3, 5}, {4, 5}};
        double[] y = {10, 20, 30, 40};
        RidgeRegression model = new RidgeRegression(1e-6);
        model.fit(x, y);
        assertEquals(50.0, model.predict(new double[]{5, 5}), 0.05);
    }

    @Test
    void strongerRegularisationPullsPredictionsTowardsTheMean() {
        double[][] x = {{1}, {2}, {3}, {4}, {5}};
        double[] y = {10, 20, 30, 40, 50};
        RidgeRegression weak = new RidgeRegression(1e-6);
        RidgeRegression strong = new RidgeRegression(1000);
        weak.fit(x, y);
        strong.fit(x, y);
        double mean = 30;
        double probe = 5;
        assertTrue(Math.abs(strong.predict(new double[]{probe}) - mean) < Math.abs(weak.predict(new double[]{probe}) - mean));
    }
}
