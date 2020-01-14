package org.github.im1235.eie.calibration;

import org.github.im1235.eie.IntensityInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests concrete A and k solvers on generated spread - intensity data
 */
public class AkSolversTest {

    public AkSolversTest() {

    }

    static double a = 10;
    static double k = 1.5;
    static double eps = 1e-10; // machine epsilon


    @DisplayName("Testing A and k solvers")
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("solverProvider")
    void testAkRSolver(AbstractAkSolver s, double[] intensities) {
        // solve A and k
        double[] sln = s.solveAk(intensities);
        // assert equalities with configuration A and k
        assertEquals(a, sln[0], eps, "A error");
        assertEquals(k, sln[1], eps, "k error");
    }


    static Stream<Arguments> solverProvider() {
        double[] spread = new double[]{1, 2, 3, 4, 5};  // test spreads
        double[] intensities = new double[spread.length];
        for (int i = 0; i < spread.length; i++) {
            // calculate intensities
            intensities[i] = IntensityInfo.getIntensity(spread[i], a, k);
        }

        // stream of solvers to be tested
        return Stream.of(
                Arguments.of(new AkMultiCurveSolver(spread), intensities),
                Arguments.of(new AkRegressionSolver(spread), intensities)
        );
    }
}
