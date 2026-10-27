package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteeringControllerTest {

    private static final double LOW_SPEED_KMH = 0.0;
    private static final double MAX_CHANGE_PER_TICK_DEGREES = 60.0;

    private SteeringController controller;

    @BeforeEach
    void setUp() {
        controller = new SteeringController();
    }

    // Each row checks the requested angle, speed, clamped target, and
    // actual steering angle after ONE tick from the fresh zero-angle state.
    @ParameterizedTest(name = "request={0}, speed={1}: target={2}, current={3}")
    @CsvSource({
            "0.0,     0.0,       0.0,     0.0",
            "30.0,    0.0,      30.0,    30.0",
            "-30.0,   0.0,     -30.0,   -30.0",
            "60.0,    0.0,      60.0,    60.0",
            "61.0,    0.0,      61.0,    60.0",
            "-61.0,   0.0,     -61.0,   -60.0",
            "450.0, 119.9999,  450.0,    60.0",
            "450.0, 120.0,     450.0,    60.0",
            "451.0, 120.0,     450.0,    60.0",
            "-451.0,120.0,    -450.0,   -60.0",
            "90.0,  120.0001,   90.0,    60.0",
            "91.0,  120.0001,   90.0,    60.0",
            "-91.0, 120.0001,  -90.0,   -60.0"
    })
    void update_whenGivenValidInputs_clampsTargetAndLimitsMovement(
            double requestedAngle, double speedKmh,
            double expectedTarget, double expectedCurrent) {

        double actualCurrent = controller.update(requestedAngle, speedKmh);

        assertEquals(expectedCurrent, actualCurrent);
        assertEquals(expectedCurrent, controller.getCurrentAngleDegrees());
        assertEquals(expectedTarget, controller.getTargetAngleDegrees());
    }

    @Test
    void update_whenRepeated_reachesTargetWithoutOvershooting() {
        double[] actualAngles = {
                controller.update(150.0, LOW_SPEED_KMH),
                controller.update(150.0, LOW_SPEED_KMH),
                controller.update(150.0, LOW_SPEED_KMH),
                controller.update(150.0, LOW_SPEED_KMH)
        };

        assertArrayEquals(new double[]{60.0, 120.0, 150.0, 150.0}, actualAngles);
        assertEquals(150.0, controller.getTargetAngleDegrees());
    }

    @Test
    void update_whenTargetChangesDirection_movesBackAtMostSixtyDegreesPerTick() {
        controller.update(120.0, LOW_SPEED_KMH);
        controller.update(120.0, LOW_SPEED_KMH);

        double[] actualAngles = {
                controller.update(-180.0, LOW_SPEED_KMH),
                controller.update(-180.0, LOW_SPEED_KMH),
                controller.update(-180.0, LOW_SPEED_KMH)
        };

        assertArrayEquals(new double[]{60.0, 0.0, -60.0}, actualAngles);
        assertEquals(-180.0, controller.getTargetAngleDegrees());
    }

    @Test
    void update_whenSwitchingToHighSpeed_clampsTargetButMovesCurrentGradually() {
        moveToAngle(240.0);

        double[] actualAngles = {
                controller.update(450.0, 121.0),
                controller.update(450.0, 121.0),
                controller.update(450.0, 121.0)
        };

        assertArrayEquals(new double[]{180.0, 120.0, 90.0}, actualAngles);
        assertEquals(90.0, controller.getTargetAngleDegrees());
    }

    @Test
    void update_whenRequestedAngleIsNaN_throwsAndPreservesState() {
        controller.update(25.0, LOW_SPEED_KMH);

        assertThrows(IllegalArgumentException.class,
                () -> controller.update(Double.NaN, LOW_SPEED_KMH));
        assertEquals(25.0, controller.getCurrentAngleDegrees());
        assertEquals(25.0, controller.getTargetAngleDegrees());
    }

    @Test
    void update_whenRequestedAngleIsPositiveInfinity_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(Double.POSITIVE_INFINITY, LOW_SPEED_KMH));
    }

    @Test
    void update_whenRequestedAngleIsNegativeInfinity_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(Double.NEGATIVE_INFINITY, LOW_SPEED_KMH));
    }

    @Test
    void update_whenSpeedIsNegative_throwsAndPreservesState() {
        controller.update(25.0, LOW_SPEED_KMH);

        assertThrows(IllegalArgumentException.class,
                () -> controller.update(90.0, -0.1));
        assertEquals(25.0, controller.getCurrentAngleDegrees());
        assertEquals(25.0, controller.getTargetAngleDegrees());
    }

    @Test
    void update_whenSpeedIsNaN_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(30.0, Double.NaN));
    }

    @Test
    void update_whenSpeedIsPositiveInfinity_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(30.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void update_whenSpeedIsNegativeInfinity_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(30.0, Double.NEGATIVE_INFINITY));
    }

    // Different initial steering positions become test inputs for selfCenter().
    // This tests a DIFFERENT production method from the @CsvSource test above.
    @ParameterizedTest(name = "start={0}: angle after selfCenter={1}")
    @MethodSource("selfCenterCases")
    void selfCenter_whenStartingAtVariousAngles_movesTowardZero(
            double initialAngle, double expectedAngle) {
        moveToAngle(initialAngle);

        double actualAngle = controller.selfCenter();

        assertEquals(expectedAngle, actualAngle);
        assertEquals(0.0, controller.getTargetAngleDegrees());
    }

    private static Stream<Arguments> selfCenterCases() {
        return Stream.of(
                Arguments.of(0.0, 0.0),
                Arguments.of(30.0, 0.0),
                Arguments.of(-30.0, 0.0),
                Arguments.of(60.0, 0.0),
                Arguments.of(-60.0, 0.0),
                Arguments.of(61.0, 1.0),
                Arguments.of(-61.0, -1.0),
                Arguments.of(180.0, 120.0),
                Arguments.of(-180.0, -120.0),
                Arguments.of(450.0, 390.0),
                Arguments.of(-450.0, -390.0)
        );
    }

    @Test
    void selfCenter_whenRepeated_eventuallyReachesZeroAndStaysThere() {
        moveToAngle(180.0);

        double[] actualAngles = {
                controller.selfCenter(),
                controller.selfCenter(),
                controller.selfCenter(),
                controller.selfCenter()
        };

        assertArrayEquals(new double[]{120.0, 60.0, 0.0, 0.0}, actualAngles);
        assertEquals(0.0, controller.getTargetAngleDegrees());
    }

    private void moveToAngle(double angleDegrees) {
        int ticks = (int) Math.ceil(Math.abs(angleDegrees) / MAX_CHANGE_PER_TICK_DEGREES);
        for (int i = 0; i < ticks; i++) {
            controller.update(angleDegrees, LOW_SPEED_KMH);
        }
    }
}