package org.github.im1235.eie.calibration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests spread - intensity curve on generated data
 */
class SpreadIntensityCurveTest {

    public SpreadIntensityCurveTest() {
    }

    /**
     * Creates and tests spread intensity curve
     * uses generated data
     */
    @Test
    void estimateAK() throws NoSuchFieldException, IllegalAccessException {

        float tickSize = 1; // used as spreadStep
        int nSpreads = 10; // number of tested spreads from 0 to (nSpread-1)*tickSize
        int w = 10000;  // sliding window

        float p0 = 1000; //start price

        float priceRef = p0;
        float priceFill; // Bid price
        Random rng = new Random();

        Field lambdaEstimatesField = SpreadIntensityCurve.class.getDeclaredField("intensityEstimates");
        lambdaEstimatesField.setAccessible(true);

        AkSolverFactory sf = new AkSolverFactory(AkSolverFactory.SolverType.MULTI_CURVE);
        SpreadIntensityCurve est = new SpreadIntensityCurve(tickSize, nSpreads, 1, sf);

        for (int i = 0; i < w; i++) {
            priceRef += rng.nextGaussian(); // simulate mid price
            priceFill = priceRef - tickSize; // bid
            est.onTick(priceRef, priceFill, i, 0); // push data to estimator
        }

        est.estimateAk(w - 1, 0); // run estimete
        double[] lambdaEstimates = (double[]) lambdaEstimatesField.get(est);

        for (int i = 1; i < lambdaEstimates.length; i++) {
            // assert correct sized of lambda
            assertTrue(lambdaEstimates[i] < lambdaEstimates[i - 1]);
        }

    }

}