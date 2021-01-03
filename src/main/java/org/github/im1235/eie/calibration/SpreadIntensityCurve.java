package org.github.im1235.eie.calibration;

import org.github.im1235.eie.EstimationExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds Spread δ (X) - Intensity λ (Y) curve.
 * Returns A and k estimates provided by specified solver
 */
public class SpreadIntensityCurve {

    private final EmpiricalIntensityEstimator[] intensityEstimators; // λ estimator for each of nSpreads
    private double[] intensityEstimates; // estimated intensities
    private final AbstractAkSolver akSolver; // Solves for A and k based on estimated intensities


    /**
     * @param spreadStep    smallest spread used in estimates, negative for buy and positive for sell limit orders
     * @param nSpreads      number of spreads to test, spreads are multiples of (1,2,..nSpreads) * spreadStep
     * @param dt
     * @param solverFactory Ak estimator factory
     */
    public SpreadIntensityCurve(double spreadStep, int nSpreads, long dt, AkSolverFactory solverFactory) {

        this.intensityEstimators = new EmpiricalIntensityEstimator[nSpreads];
        double[] spreadSpecification = new double[nSpreads];
        this.intensityEstimates = new double[this.intensityEstimators.length];
        IntStream.range(0, nSpreads).forEach(i -> {
            spreadSpecification[i] = i * spreadStep;
            this.intensityEstimators[i] = new EmpiricalIntensityEstimator(spreadSpecification[i], Math.signum(spreadStep), dt);
        });
        this.akSolver = solverFactory.getSolver(spreadSpecification);

    }


    /**
     * @param refPrice    reference price (mid price)
     * @param fillPrice   price at which all orders have been fully filled
     * @param ts          current time stamp
     * @param windowStart start of evaluation window, older data is deleted
     */
    public synchronized void onTick(double refPrice, double fillPrice, long ts, long windowStart) {
        Arrays.stream(this.intensityEstimators).forEach(ie -> ie.onTick(refPrice, fillPrice, ts, windowStart));
    }


    /**
     * async parallel implementation of {@link #onTick}
     *
     * @param refPrice
     * @param fillPrice
     * @param ts
     * @param windowStart
     * @return
     */
    public synchronized Future<Void> onTickAsync(double refPrice, double fillPrice, long ts, long windowStart) {

        return EstimationExecutor.submit(() -> {
            List<Callable<Void>> tickTasks = Arrays.stream(this.intensityEstimators)
                    .map(ie -> (Callable<Void>) () -> {
                        ie.onTick(refPrice, fillPrice, ts, windowStart);
                        return null;
                    })
                    .collect(Collectors.toList());
            EstimationExecutor.invokeAll(tickTasks);
            return null;
        });
    }


    /**
     * @param ts          current time stamp
     * @param windowStart start of evaluation window, older data is deleted
     * @return double[]{A, k} , estimate of A and k
     */
    public synchronized double[] estimateAk(long ts, long windowStart) {
        IntStream.range(0, this.intensityEstimators.length)
                .forEach(i -> {
                    intensityEstimates[i] = this.intensityEstimators[i].estimateIntensity(ts, windowStart);
                });
        return this.akSolver.solveAk(intensityEstimates);
    }


    /**
     * async parallel implementation of {@link #estimateAk}
     *
     * @param ts          current time stamp
     * @param windowStart start of evaluation window, data before is deleted
     * @return
     */
    public synchronized Future<double[]> estimateAkAsync(long ts, long windowStart) {
        return EstimationExecutor.submit(() -> {
                    List<Callable<Void>> estimateTasks = IntStream.range(0, this.intensityEstimators.length)
                            .mapToObj(i -> (Callable<Void>) () -> {
                                intensityEstimates[i] = this.intensityEstimators[i].estimateIntensity(ts, windowStart);
                                return null;
                            })
                            .collect(Collectors.toList());
                    EstimationExecutor.invokeAll(estimateTasks);
                    return this.akSolver.solveAk(intensityEstimates);
                }
        );
    }

}
