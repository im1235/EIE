package org.github.im1235.eie.calibration;

import java.util.Arrays;

/**
 * Abstract solver of A and k
 */
abstract class AbstractAkSolver {

    final double[] spreadSpecification;

    /**
     * @param spreadSpecification Array of spreads (X axis of Spread - Intensity curve)
     */
    AbstractAkSolver(double[] spreadSpecification) {
        this.spreadSpecification = Arrays.stream(spreadSpecification).map(s -> Math.abs(s)).toArray();
    }

    /**
     * @param intensities Array of intensities (Y axis of Spread - Intensity curve)
     * @return implemented method should return array with estimated A and k [A, k]
     */
    abstract double[] solveAk(double[] intensities);

}
