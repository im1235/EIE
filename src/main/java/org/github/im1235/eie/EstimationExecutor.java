package org.github.im1235.eie;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Provides unified access to global executor service
 */
public class EstimationExecutor {

    private static ExecutorService executor;

    /**
     * @param executor global executor that will run estimates
     */
    public static void setExecutor(ExecutorService executor) {
        EstimationExecutor.executor = executor;
    }

    public static <T> Future<T> submit(Callable<T> c) {
        return EstimationExecutor.executor.submit(c);
    }

    public static <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> cc) throws InterruptedException {
        return EstimationExecutor.executor.invokeAll(cc);
    }

}
