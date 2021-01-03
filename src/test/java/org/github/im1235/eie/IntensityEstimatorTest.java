package org.github.im1235.eie;

import me.tongfei.progressbar.ProgressBar;
import org.github.im1235.eie.calibration.AkSolverFactory;
import org.github.im1235.eie.calibration.SpreadIntensityCurve;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests estimators on historical data in "src/test/resources/tick.csv
 * Detailed test output is saved to target/intensity-log/ folder
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntensityEstimatorTest {
    public IntensityEstimatorTest() {
    }

    /**
     * estimator configuration
     */
    double spreadStep = 0.00001;
    int nSteps = 5;
    long w = 1000 * 60 *10 ; // sliding window 30 min
    long dt = 1000 * 15; // time scaling 15 sec


    /**
     * Test spread
     */
    double testSpread = 3 * spreadStep;

    List<TickData> testData = new LinkedList<>(); // holds preloaded test data
    Field intensityEstimatesField, sellLimitEstimatorField, buyLimitEstimatorField; // fields for accessing private object internals

    @BeforeAll
    void prepareTest() throws IOException, NoSuchFieldException {

        // load test data
        testData = Files.lines(Paths.get("src/test/resources/tick.csv"))
                .skip(1)
                .map(line -> line.split(","))
                .map(parts -> new TickData(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Long.parseLong(parts[2])))
                .collect(Collectors.toList());

        // enable access to private fields
        intensityEstimatesField = SpreadIntensityCurve.class.getDeclaredField("intensityEstimates");
        intensityEstimatesField.setAccessible(true);

        sellLimitEstimatorField = IntensityEstimator.class.getDeclaredField("sellExecutionIntensity");
        sellLimitEstimatorField.setAccessible(true);

        buyLimitEstimatorField = IntensityEstimator.class.getDeclaredField("buyExecutionIntensity");
        buyLimitEstimatorField.setAccessible(true);

    }


    @Test
    void buyEstimatorTest() throws IllegalAccessException {
        // create solver factory
        AkSolverFactory sf = new AkSolverFactory(AkSolverFactory.SolverType.MULTI_CURVE);
        // create buy intensity estimator
        SpreadIntensityCurve buyExecutionIntensity = new SpreadIntensityCurve(-spreadStep, nSteps, dt, sf);
        double[] buyEmpiricalIntensities = (double[]) intensityEstimatesField.get(buyExecutionIntensity);

        long calibrationTs = this.testData.get(0).ts + w;
           for (final TickData td : ProgressBar.wrap(this.testData, "buy test: ")) {
                long windowStart = td.ts - w;
                // send data to estimator
                buyExecutionIntensity.onTick((td.b+td.a)/2, td.a, td.ts, windowStart);

                if (calibrationTs<= td.ts) {
                    // run estimates and get resulting IntensityInfo
                    double[] estimates=  buyExecutionIntensity.estimateAk(td.ts, windowStart);
                    int iPrev = 0;
                    for (int i = 1; i < buyEmpiricalIntensities.length; i++) {
                        assertTrue(buyEmpiricalIntensities[iPrev] > buyEmpiricalIntensities[i],
                                String.format("Buy λ estimate %d/%d == %.6f/%6f",
                                        iPrev,
                                        i,
                                        buyEmpiricalIntensities[iPrev],
                                        buyEmpiricalIntensities[i]));
                        iPrev = i;
                    }
                }
           }
    }


    @Test
    void sellEstimatorTest() throws IllegalAccessException {
        // create solver factory
        AkSolverFactory sf = new AkSolverFactory(AkSolverFactory.SolverType.MULTI_CURVE);
        // create sell intensity estimator
        SpreadIntensityCurve sellExecutionIntensity = new SpreadIntensityCurve(spreadStep, nSteps, dt, sf);
        double[] buyEmpiricalIntensities = (double[]) intensityEstimatesField.get(sellExecutionIntensity);

        long calibrationTs = this.testData.get(0).ts + w;
           for (final TickData td : ProgressBar.wrap(this.testData, "sell test: ")) {
                long windowStart = td.ts - w;
                // send data to estimator
                sellExecutionIntensity.onTick((td.b+td.a)/2, td.b, td.ts, windowStart);

                if (calibrationTs<= td.ts) {
                    // run estimates and get resulting IntensityInfo
                    double[] estimates=  sellExecutionIntensity.estimateAk(td.ts, windowStart);
                    int iPrev = 0;
                    for (int i = 1; i < buyEmpiricalIntensities.length; i++) {
                        assertTrue(buyEmpiricalIntensities[iPrev] > buyEmpiricalIntensities[i],
                                String.format("Buy λ estimate %d/%d == %.6f/%6f",
                                        iPrev,
                                        i,
                                        buyEmpiricalIntensities[iPrev],
                                        buyEmpiricalIntensities[i]));
                        iPrev = i;
                    }
                }
           }
    }


    @Test
    void singleThreadTest() throws IOException, IllegalAccessException {
        // create solver factory
        AkSolverFactory sf = new AkSolverFactory(AkSolverFactory.SolverType.MULTI_CURVE);
        // create intensity estimator
        IntensityEstimator ie = new IntensityEstimator(spreadStep, nSteps, w, dt, sf);

        try (estimationOutputWriter eow = new estimationOutputWriter("serialEstimations.csv", nSteps)) {

            for (final TickData td : ProgressBar.wrap(this.testData, "Serial test: ")) {
                // send data to estimator
                if (ie.onTick(td.b, td.a, td.ts)) {
                    // run estimates and get resulting IntensityInfo
                    IntensityInfo intensityInfo = ie.estimate(td.ts);

                    // assert values are correct
                    double[][] buySellEmpiricalIntensities = assertCorrectIntensities(ie);

                    // write detailed log
                    eow.writeLine(td.b, td.a, td.ts,
                            intensityInfo,
                            buySellEmpiricalIntensities[0], buySellEmpiricalIntensities[1]);

                }
            }
        }
    }


    @Test
    void multiThreadTest() throws IOException, IllegalAccessException, ExecutionException, InterruptedException {

        // configure estimators global executor
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 3);
        EstimationExecutor.setExecutor(executor);

        // create solver factory
        AkSolverFactory sf = new AkSolverFactory(AkSolverFactory.SolverType.MULTI_CURVE);
        // create intensity estimator
        IntensityEstimator ie = new IntensityEstimator(spreadStep, nSteps, w, dt, sf);

        try (estimationOutputWriter eow = new estimationOutputWriter("parallelEstimations.csv", nSteps)) {

            for (final TickData td : ProgressBar.wrap(this.testData, "Parallel test: ")) {
                // send data to estimator
                Future<Boolean> tickResult = ie.onTickAsync(td.b, td.a, td.ts);
                if (tickResult.get()) {
                    // run estimates
                    Future<IntensityInfo> result = ie.estimateAsync(td.ts);

                    // once done get results
                    IntensityInfo intensityInfo = result.get();

                    // assert values are correct
                    double[][] buySellEmpiricalIntensities = assertCorrectIntensities(ie);

                    // write detailed log
                    eow.writeLine(td.b, td.a, td.ts,
                            intensityInfo,
                            buySellEmpiricalIntensities[0], buySellEmpiricalIntensities[1]);

                }
            }
        }
    }

    /**
     * Tests if single threaded and multi threaded execution give same result
     */
    @Test
    void identityTest() throws IOException, IllegalAccessException, ExecutionException, InterruptedException {

        // configure estimators global executor
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 3);
        EstimationExecutor.setExecutor(executor);

        // create solver factory
        AkSolverFactory sf = new AkSolverFactory(AkSolverFactory.SolverType.MULTI_CURVE);
        // create single thread intensity estimator
        IntensityEstimator ie_s = new IntensityEstimator(spreadStep, nSteps, w, dt, sf);

        // create multi thread intensity estimator
        IntensityEstimator ie_m = new IntensityEstimator(spreadStep, nSteps, w, dt, sf);


        for (final TickData td : ProgressBar.wrap(this.testData, "Identity test: ")) {
            // send data to estimator
            Future<Boolean> tickResult_m = ie_m.onTickAsync(td.b, td.a, td.ts);
            ie_s.onTick(td.b, td.a, td.ts);
            if (tickResult_m.get()) {

                // once you get results
                IntensityInfo ii_m = ie_m.estimateAsync(td.ts).get();
                IntensityInfo ii_s = ie_s.estimate(td.ts);

                // assert values are correct
                assertEquals(ii_m.getBuyFillIntensity(testSpread), ii_s.getBuyFillIntensity(testSpread));

            }
        }
    }


    double[][] assertCorrectIntensities(IntensityEstimator fre) throws IllegalAccessException {

        SpreadIntensityCurve buyCurveBuilder = (SpreadIntensityCurve) buyLimitEstimatorField.get(fre);
        SpreadIntensityCurve sellCurveBuilder = (SpreadIntensityCurve) sellLimitEstimatorField.get(fre);
        double[] buyEmpiricalIntensities = (double[]) intensityEstimatesField.get(buyCurveBuilder);
        double[] sellEmpiricalIntensities = (double[]) intensityEstimatesField.get(sellCurveBuilder);

        assertEquals(buyEmpiricalIntensities.length, sellEmpiricalIntensities.length);
        int iPrev = 0;
        // assert increase of spread decreases intensity
        for (int i = 1; i < buyEmpiricalIntensities.length; i++) {

            assertTrue(buyEmpiricalIntensities[iPrev] > buyEmpiricalIntensities[i],
                    String.format("Buy λ estimate %d/%d == %.6f/%6f",
                            iPrev,
                            i,
                            buyEmpiricalIntensities[iPrev],
                            buyEmpiricalIntensities[i]));

            assertTrue(sellEmpiricalIntensities[iPrev] > sellEmpiricalIntensities[i],
                    String.format("Sell λ estimate %d/%d == %.6f/%6f",
                            iPrev,
                            i,
                            sellEmpiricalIntensities[iPrev],
                            sellEmpiricalIntensities[i]));

            iPrev = i;
        }
        return new double[][]{buyEmpiricalIntensities, sellEmpiricalIntensities};
    }

    /**
     * Container for test data
     */
    class TickData {
        public final double b, a;
        public final long ts;

        public TickData(double b, double a, long ts) {
            this.b = b;
            this.a = a;
            this.ts = ts;
        }
    }

    /**
     * Helper class for formatting and writing output .csv with detailed estimation info
     */
    class estimationOutputWriter implements Closeable {
        private final BufferedWriter writer;
        private final StringBuilder sb;
        private final String priceFormat = "%.5f";
        private final String intensityFormat = "%.5f";

        public estimationOutputWriter(String fileName, int nSpreads) throws IOException {

            String outputDir = "target/intensity-log/";

            Path dirPath = Paths.get(outputDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            this.writer = Files.newBufferedWriter(Paths.get("target/intensity-log/" + fileName));
            this.sb = new StringBuilder();
            // Write header
            sb.append("bid,ask,mid price,ts,buy λ(test δ),sell λ(test δ),");
            IntStream.range(0, nSpreads).forEach(i -> sb.append("buy λ(").append(i).append("δ),"));
            IntStream.range(0, nSpreads).forEach(i -> sb.append("sell λ(").append(i).append("δ),"));
            sb.append("buy δ(test λ),sell δ(test λ), buy A, buy k, sell A, sell k\n");
            writer.write(sb.toString());
        }


        public void writeLine(double bid,
                              double ask,
                              long ts,
                              IntensityInfo intensityInfo,
                              double[] buyEmpiricalIntensities,
                              double[] sellEmpiricalIntensities) throws IOException {

            double buyIntensity = intensityInfo.getBuyFillIntensity(testSpread);
            double sellIntensity = intensityInfo.getSellFillIntensity(testSpread);
            double buySpread = intensityInfo.getBuySpread(buyIntensity);
            double sellSpread = intensityInfo.getSellSpread(sellIntensity);

            // reset string builder
            this.sb.delete(0, sb.length());

            sb.append(String.format(priceFormat, bid)).append(",");
            sb.append(String.format(priceFormat, ask)).append(",");
            sb.append(String.format(priceFormat, (bid + ask) / 2)).append(",");
            sb.append(ts).append(",");
            sb.append(String.format(intensityFormat, buyIntensity)).append(",");
            sb.append(String.format(intensityFormat, sellIntensity)).append(",");

            IntStream.range(0, buyEmpiricalIntensities.length)
                    .forEach(i -> sb.append(String.format(intensityFormat, buyEmpiricalIntensities[i])).append(","));

            IntStream.range(0, sellEmpiricalIntensities.length)
                    .forEach(i -> sb.append(String.format(intensityFormat, sellEmpiricalIntensities[i])).append(","));

            sb.append(String.format(priceFormat, buySpread)).append(",");
            sb.append(String.format(priceFormat, sellSpread)).append(",");
            sb.append(String.format(priceFormat, intensityInfo.buyA)).append(",");
            sb.append(String.format(intensityFormat, intensityInfo.buyK)).append(",");
            sb.append(String.format(priceFormat, intensityInfo.sellA)).append(",");
            sb.append(String.format(intensityFormat, intensityInfo.sellK)).append("\n");

            writer.write(sb.toString());
        }


        @Override
        public void close() throws IOException {
            writer.close();
        }

    }

}