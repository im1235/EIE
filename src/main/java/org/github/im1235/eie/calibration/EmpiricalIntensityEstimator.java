package org.github.im1235.eie.calibration;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Estimates order execution intensity (empirical lambda) for provided distance from mid price (spread)
 * <p>
 * https://pdfs.semanticscholar.org/20e5/e8364a48ef9d4b25fbf7d6e0892bf4baa265.pdf  (section 4.4.2.)
 */
class EmpiricalIntensityEstimator {

    /**
     * Implementation specifies fill criterion
     * Separate implementations for buy / sell limit orders
     */
    private abstract class Fill {
        /**
         * @param filledPrice current market price at which orders are filled
         * @param orderPrice  price of placed limit order
         * @return true if order is filled
         */
        abstract boolean isOrderFilled(double filledPrice, double orderPrice);
    }


    /**
     * Container for limit order info
     */
    private class LimitOrderTracker {
        public final long startTs;
        public final double orderPrice;

        LimitOrderTracker(double orderPrice, long ts) {
            this.orderPrice = orderPrice;
            this.startTs = ts;
        }
    }

    private final double spread;
    private final long dt;
    private final Fill fillComp;
    private boolean initializing = true;
    private double lastPrice = Double.NaN;
    private long lastLimitOrderInserted = 0;

    /**
     * trackers of limit orders that are not filled
     */
    private final LinkedList<LimitOrderTracker> liveTrackers = new LinkedList<>();
    /**
     * contains sum of start timestamps
     * live trackers wait time = current time * liveTrackers.size() - liveTrackersStartTimeSum
     * 2DO: fix overflow of liveTrackersStartTimeSum (can cause negative λ)
     */
    private long liveTrackersStartTimeSum = 0;


    /**
     * trackers of filled orders
     * long[] listItem= new long[]{start time, wait time}
     */
    private final LinkedList<long[]> finishedTrackers = new LinkedList<>();
    /**
     * finished trackers sum of waiting time
     */
    private long finishedTrackersWaitTimeSum = 0;


    /**
     * @param spread distance from mid price, use negative sign for buy limit and positive for sell limit
     * @param spreadDirection -1 for sell limit orders, 1 for buy limit
     * @param dt
     */
    EmpiricalIntensityEstimator(double spread, double spreadDirection, long dt) {
        this.spread = spread;
        this.dt = dt;
        if (spreadDirection > 0) {
            // concrete sell limit order fill comparator
            this.fillComp = new Fill() {
                @Override
                boolean isOrderFilled(double filledPrice, double orderPrice) {
                    return filledPrice > orderPrice;
                }
            };
        } else {
            // concrete buy limit order fill comparator
            this.fillComp = new Fill() {
                @Override
                boolean isOrderFilled(double filledPrice, double orderPrice) {
                    return filledPrice < orderPrice;
                }
            };
        }
    }


    /**
     * @param refPrice    reference price (mid price)
     * @param fillPrice   current market price at which orders are filled
     * @param ts          current time stamp
     * @param windowStart start of evaluation window, older data is deleted
     */
    void onTick(double refPrice, double fillPrice, long ts, long windowStart) {

        if(this.initializing){
            this.initializing = false;
            this.lastLimitOrderInserted = ts - this.dt;
        }

        // insert new tracker every dt
        while (this.lastLimitOrderInserted + this.dt < ts){
            this.lastLimitOrderInserted = this.lastLimitOrderInserted + dt;
            // add new tracker, price is last recived price
            this.liveTrackers.add(
                    new LimitOrderTracker(this.lastPrice + this.spread, this.lastLimitOrderInserted)
            );
            //add ts to sum of start timestamps
            this.liveTrackersStartTimeSum += lastLimitOrderInserted;
        }

        // insert new tracker evrey dt
        if (this.lastLimitOrderInserted + this.dt == ts){
            this.lastLimitOrderInserted = ts;
            // add new tracker, add ts to sum of start timestamps
            this.liveTrackers.add(new LimitOrderTracker(refPrice + this.spread, ts));
            this.liveTrackersStartTimeSum += ts;
        }

        this.lastPrice = refPrice;

        ListIterator<LimitOrderTracker> iter = this.liveTrackers.listIterator();

        while (iter.hasNext()) {
            LimitOrderTracker tr = iter.next();

            // check if tracker has expired
            if (windowStart > tr.startTs) {
                // remove from live trackers, subtract startTs
                iter.remove();
                this.liveTrackersStartTimeSum -= tr.startTs;
                continue;
            }

            // check if tracker is  done (order filled)
            if (this.fillComp.isOrderFilled(fillPrice, tr.orderPrice)) {
                // remove from live trackers, subtract startTs
                iter.remove();
                this.liveTrackersStartTimeSum -= tr.startTs;

                long duration = ts - tr.startTs;
                // add to finished trackers, add duration to sum
                this.finishedTrackers.add(new long[]{tr.startTs, duration});
                this.finishedTrackersWaitTimeSum += duration;
            }
        }
    }


    /**
     * @param ts          current time stamp
     * @param windowStart start of evaluation window, older data is deleted
     * @return empirical estimate of lambda (intensity)
     */
    double estimateIntensity(long ts, long windowStart) {

        // iterate over finished order trackers
        Iterator<long[]> iterDone = this.finishedTrackers.listIterator();
        while (iterDone.hasNext()) {
            long[] tr = iterDone.next();
            if (tr[0] < windowStart) {
                // remove if tracker is older than windowStart
                iterDone.remove();
                // remove duration from sum of waiting times
                finishedTrackersWaitTimeSum -= tr[1];
            }
        }

        // check if time passed from last tick
        if (!this.liveTrackers.isEmpty() && ts != this.liveTrackers.getLast().startTs) {
            // iterate over unfinished order trackers
            ListIterator<LimitOrderTracker> iterLive = this.liveTrackers.listIterator();
            while (iterLive.hasNext()) {
                LimitOrderTracker tr = iterLive.next();
                if (windowStart > tr.startTs) {
                    // remove if tracker is older than windowStart
                    iterLive.remove();
                    // remove duration from sum of waiting times
                    liveTrackersStartTimeSum -= tr.startTs;
                }
            }
        }

        return (double) this.dt * this.finishedTrackers.size() /
                (liveTrackers.size() * ts - liveTrackersStartTimeSum + finishedTrackersWaitTimeSum);

    }

}