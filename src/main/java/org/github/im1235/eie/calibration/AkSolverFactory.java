package org.github.im1235.eie.calibration;

/**
 * Factory for A k solvers
 */
public class AkSolverFactory {

    public enum SolverType {
        LOG_REGRESSION,
        MULTI_CURVE
    }

    public final SolverType solverType;

    /**
     * @param solverType
     */
    public AkSolverFactory(SolverType solverType) {
        this.solverType = solverType;
    }

    /**
     * @param spreadSpecification spreads used in estimation
     * @return concrete estimator for A and k
     */
    public AbstractAkSolver getSolver(double[] spreadSpecification) {
        switch (this.solverType) {
            case MULTI_CURVE:
                return new AkMultiCurveSolver(spreadSpecification);
            case LOG_REGRESSION:
                return new AkRegressionSolver(spreadSpecification);
            default:
                return null;
        }
    }

}
