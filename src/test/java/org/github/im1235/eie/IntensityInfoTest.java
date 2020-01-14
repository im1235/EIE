package org.github.im1235.eie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * tests calculations relating A, k, λ and δ.
 */
class IntensityInfoTest {
    public IntensityInfoTest() {
    }

    double intensity = 0.00024, spread = 0.00002, a = 0.00037, k = 20600, eps = 1e-10;

    @Test
    void getIntensity() {
        double testSpread = IntensityInfo.getSpread(intensity, a, k);
        double testIntensity = IntensityInfo.getIntensity(testSpread, a, k);
        assertEquals(testIntensity, intensity, eps);
    }

    @Test
    void getSpread() {
        double testIntensity = IntensityInfo.getIntensity(spread, a, k);
        double testSpread = IntensityInfo.getSpread(testIntensity, a, k);
        assertEquals(testSpread, spread, eps);
    }

    @Test
    void intensityInfoObject() {
        IntensityInfo ii = new IntensityInfo(a, k, a, k);
        double testSpread = IntensityInfo.getSpread(intensity, a, k);
        double testIntensity = IntensityInfo.getIntensity(spread, a, k);
        assertEquals(ii.getBuyFillIntensity(spread), testIntensity);
        assertEquals(ii.getSellFillIntensity(spread), testIntensity);
        assertEquals(ii.getBuySpread(intensity), testSpread);
        assertEquals(ii.getSellSpread(intensity), testSpread);
    }
}