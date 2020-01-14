package org.github.im1235.eie;

/**
 * Holds info about estimated execution intensity parameters A and k for buy and sell limit orders
 * Facilitates calculations of intensity and spread
 * <p>
 * λ(δ) = A * e^(-k * δ)
 * λ - Poisson order execution intensity
 * δ - spread (distance from mid price)
 * A - parameter, positively related to trading intensity
 * k - parameter, positively related to market depth
 */
public class IntensityInfo {

    public final double buyA, buyK, sellA, sellK;

    public IntensityInfo(double buyA, double buyK, double sellA, double sellK) {
        this.buyA = buyA;
        this.buyK = buyK;
        this.sellA = sellA;
        this.sellK = sellK;
    }

    public IntensityInfo(double[] buyAk, double[] sellAk) {
        this.buyA = buyAk[0];
        this.buyK = buyAk[1];
        this.sellA = sellAk[0];
        this.sellK = sellAk[1];
    }

    public double getSellFillIntensity(double spread) {
        return getIntensity(spread, this.sellA, this.sellK);
    }

    public double getBuyFillIntensity(double spread) {
        return getIntensity(spread, this.buyA, this.buyK);
    }

    public double getSellSpread(double intensity) {
        return getSpread(intensity, this.sellA, this.sellK);
    }

    public double getBuySpread(double intensity) {
        return getSpread(intensity, this.buyA, this.buyK);
    }


    /**
     * Calculate Poisson intensity λ for order with target spread and provided A and k
     *
     * @param targetSpread δ distance from mid price
     * @param a
     * @param k
     * @return intensity λ for specified spread
     */
    public static double getIntensity(double targetSpread, double a, double k) {
        return a * Math.exp(-k * targetSpread);
    }

    /**
     * Calculate spread δ (distance from mid price) for order with target execution intensity λ and provided A and k
     *
     * @param targetIntensity poisson intensity λ
     * @param a
     * @param k
     * @return spread δ (distance from mid price)
     */
    public static double getSpread(double targetIntensity, double a, double k) {
        return -(Math.log(targetIntensity / a)) / k;
    }
}
