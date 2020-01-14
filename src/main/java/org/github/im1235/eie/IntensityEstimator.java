package org.github.im1235.eie;

import org.github.im1235.eie.calibration.AkSolverFactory;
import org.github.im1235.eie.calibration.SpreadIntensityCurve;

import java.util.concurrent.Future;

/**
 * Estimates buy/sell limit order execution intensities , calibrates A and k parameters
 */
public class IntensityEstimator {

    private final SpreadIntensityCurve sellExecutionIntensity;
    private final SpreadIntensityCurve buyExecutionIntensity;

    private Long initDoneTS = null;
    private boolean isInitializing = true;
    private boolean isInitialized = false;
    private final long w;


    /**
     * @param spreadStep    smallest spread used in estimates, must be greater than or equal to tick size
     * @param nSpreads      number of spreads to test, spreads are multiples of (0,1,..nSpreads-1) * +/-spreadStep
     * @param w             sliding window width in time units
     * @param dt            time scaling quant in time units
     * @param solverFactory
     */
    public IntensityEstimator(double spreadStep, int nSpreads, long w, long dt, AkSolverFactory solverFactory) {
        this.w = w;
        this.sellExecutionIntensity = new SpreadIntensityCurve(spreadStep, nSpreads, dt, solverFactory);
        this.buyExecutionIntensity = new SpreadIntensityCurve(-spreadStep, nSpreads, dt, solverFactory);
    }


    /**
     * @param bid best market bid price
     * @param ask best market ask price
     * @param ts  time stamp
     * @return true once estimator has been initialized with sufficient data
     */
    public synchronized boolean onTick(double bid, double ask, long ts) {
        if (this.isInitializing) {
            init(ts);
        }
        double midPrice = (bid + ask) / 2;
        long windowStart = ts - this.w;
        this.sellExecutionIntensity.onTick(midPrice, bid, ts, windowStart);
        this.buyExecutionIntensity.onTick(midPrice, ask, ts, windowStart);
        return this.isInitialized;
    }


    /**
     * Async parallel implementation of  {@link #onTick}
     *
     * @param bid
     * @param ask
     * @param ts
     * @return
     */
    public synchronized Future<Boolean> onTickAsync(double bid, double ask, long ts) {
        return EstimationExecutor.submit(() -> {
            if (this.isInitializing) {
                init(ts);
            }
            double midPrice = (bid + ask) / 2;
            long windowStart = ts - this.w;
            Future<Void> sellResult = this.sellExecutionIntensity.onTickAsync(midPrice, bid, ts, windowStart);
            Future<Void> buyResult = this.buyExecutionIntensity.onTickAsync(midPrice, ask, ts, windowStart);
            sellResult.get();
            buyResult.get();
            return this.isInitialized;
        });
    }


    /**
     * Sets estimator to initialized once w time has elapsed
     *
     * @param ts
     */
    private void init(long ts) {
        if (this.initDoneTS == null) {
            this.initDoneTS = ts + this.w;
            return;
        }
        if (this.initDoneTS <= ts) {
            this.isInitialized = true;
            this.isInitializing = false;
        }
    }

    /**
     * performs estimation of all parameters
     *
     * @param ts
     */
    public synchronized IntensityInfo estimate(long ts) {
        long windowStart = ts - this.w;
        return new IntensityInfo(
                this.buyExecutionIntensity.estimateAk(ts, windowStart),
                this.sellExecutionIntensity.estimateAk(ts, windowStart)
        );
    }

    /**
     * Async parallel implementation of  {@link #estimate}
     *
     * @param ts
     */
    public synchronized Future<IntensityInfo> estimateAsync(long ts) {
        return EstimationExecutor.submit(() -> {
            long windowStart = ts - this.w;
            Future<double[]> sellEstResult = this.sellExecutionIntensity.estimateAkAsync(ts, windowStart);
            Future<double[]> buyEstResult = this.buyExecutionIntensity.estimateAkAsync(ts, windowStart);
            return new IntensityInfo(buyEstResult.get(), sellEstResult.get());
        });
    }

}
