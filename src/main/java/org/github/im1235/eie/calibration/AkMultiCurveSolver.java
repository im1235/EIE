package org.github.im1235.eie.calibration;

import org.apache.commons.math3.stat.descriptive.moment.Mean;

/**
 * Implementation, solver of A and k,
 * <p>
 * from set of N points creates Ns = (N*(N-1))/2 unique pairs for points (δx,λx)(δy,λy).
 * For each set of points solves the following set of equations for A' and k' :
 * λx = A' e-k'δx
 * λy = A' e-k'δy
 * Final estimates are A = mean(A'1,A'2,... A'Ns) and k = mean(k'1,k'2,... k'Ns)
 */
class AkMultiCurveSolver extends AbstractAkSolver {

    private final double[] kEstimates;
    private final double[] aEstimates;
    private final Mean me = new Mean();

    /**
     * @param spreadSpecification Array of spreads (X axis of Spread - Intensity curve)
     */
    AkMultiCurveSolver(double[] spreadSpecification) {
        super(spreadSpecification);
        int nEstimates = spreadSpecification.length * (spreadSpecification.length - 1) / 2;
        this.kEstimates = new double[nEstimates];
        this.aEstimates = new double[nEstimates];
    }

    /**
     * @param intensities Array of intensities (Y axis of Spread - Intensity curve)
     * @return array with estimated A and k [A, k]
     */
    @Override
    double[] solveAk(double[] intensities) {
        int estIdx = 0;
        for (int i = 0; i < intensities.length - 1; i++) {
            for (int j = i + 1; j < intensities.length; j++) {
                kEstimates[estIdx] = Math.log(intensities[j] / intensities[i]) / (spreadSpecification[i] - spreadSpecification[j]);
                aEstimates[estIdx] = intensities[i] * Math.exp(kEstimates[estIdx] * spreadSpecification[i]);
                estIdx++;
            }
        }
        return new double[]{this.me.evaluate(aEstimates), this.me.evaluate(kEstimates)};
    }

}
