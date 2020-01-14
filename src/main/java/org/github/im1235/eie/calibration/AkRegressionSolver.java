package org.github.im1235.eie.calibration;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.stream.IntStream;

/**
 * Implementation, solver of A and k
 * performs OLS regression of log(λ) on δ.
 * k = -slope
 * A = e^intercept
 */
class AkRegressionSolver extends AbstractAkSolver {

    private final SimpleRegression simpleRegression = new SimpleRegression(true);

    /**
     * @param spreadSpecification Array of spreads (X axis of Spread - Intensity curve)
     */
    AkRegressionSolver(double[] spreadSpecification) {
        super(spreadSpecification);
    }

    /**
     * @param intensities Array of intensities (Y axis of Spread - Intensity curve)
     * @return array with estimated A and k [A, k]
     */
    @Override
    double[] solveAk(double[] intensities) {
        this.simpleRegression.clear();

        IntStream.range(0, spreadSpecification.length)
                .forEach(i -> simpleRegression.addData(super.spreadSpecification[i], Math.log(intensities[i])));

        return new double[]{Math.exp(simpleRegression.getIntercept()), -simpleRegression.getSlope()};
    }

}
