package com.subshift.backend.ml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests: no Spring, no database. */
class KMeansTest {

    private static final double[][] TWO_GROUPS = {
            {1, 1}, {1.2, 0.9}, {0.8, 1.1}, {1.1, 1.0},
            {10, 10}, {10.2, 9.9}, {9.8, 10.1}, {10.1, 10.0}};

    @Test
    void separatesTwoObviousGroups() {
        KMeans.Result result = KMeans.fit(TWO_GROUPS, 2, 42L, 5);
        int[] a = result.assignment();
        for (int i = 1; i < 4; i++) assertEquals(a[0], a[i]);
        for (int i = 5; i < 8; i++) assertEquals(a[4], a[i]);
        assertNotEquals(a[0], a[4]);
    }

    @Test
    void silhouetteIsHighForWellSeparatedClusters() {
        KMeans.Result result = KMeans.fit(TWO_GROUPS, 2, 42L, 5);
        assertTrue(KMeans.silhouette(TWO_GROUPS, result.assignment(), 2) > 0.9);
    }

    @Test
    void sameSeedGivesSameResult() {
        KMeans.Result first = KMeans.fit(TWO_GROUPS, 2, 7L, 3);
        KMeans.Result second = KMeans.fit(TWO_GROUPS, 2, 7L, 3);
        assertArrayEquals(first.assignment(), second.assignment());
        assertEquals(first.inertia(), second.inertia(), 1e-12);
    }

    @Test
    void moreClustersNeverIncreaseInertia() {
        double two = KMeans.fit(TWO_GROUPS, 2, 1L, 5).inertia();
        double four = KMeans.fit(TWO_GROUPS, 4, 1L, 5).inertia();
        assertTrue(four <= two + 1e-9);
    }

    @Test
    void invalidKIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(TWO_GROUPS, 0, 1L, 1));
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(TWO_GROUPS, 9, 1L, 1));
    }
}
