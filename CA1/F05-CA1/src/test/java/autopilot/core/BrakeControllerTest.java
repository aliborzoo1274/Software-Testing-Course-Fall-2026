package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrakeControllerTest {

    private static final double STATIONARY_SPEED_KMH = 0.0;
    private static final double HIGH_SPEED_KMH = 60.0;

    private BrakeController controller;

    @BeforeEach
    void setUp() {
        controller = new BrakeController();
    }

    @ParameterizedTest(name = "requested pressure={0}: applied pressure={1}")
    @CsvSource({
            "0.0,   0.0",
            "25.0, 25.0",
            "80.0, 80.0",
            "150.0, 100.0"
    })
    void apply_whenAbsIsNotActive_limitsPressureToMaximum(
            double requestedPressure, double expectedPressure) {
        double actualPressure =
                controller.apply(requestedPressure, STATIONARY_SPEED_KMH,
                        GearBox.Gear.D);

        assertEquals(expectedPressure, actualPressure);
        assertEquals(expectedPressure, controller.getPressure());
        assertFalse(controller.isWarningActive());
    }

    @Test
    void apply_whenHighPressureAtHighSpeed_alternatesAbsPulses() {
        double firstPressure =
                controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);
        double secondPressure =
                controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);
        double thirdPressure =
                controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);

        assertEquals(90.0, firstPressure);
        assertEquals(60.0, secondPressure);
        assertEquals(90.0, thirdPressure);
    }

    @Test
    void apply_whenAbsPressureIsAtThreshold_doesNotPulse() {
        double actualPressure =
                controller.apply(80.0, HIGH_SPEED_KMH, GearBox.Gear.D);

        assertEquals(80.0, actualPressure);
    }

    @Test
    void apply_whenAbsWasActiveAndNextRequestIsNormal_resetsAbsSequence() {
        controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);

        controller.apply(50.0, HIGH_SPEED_KMH, GearBox.Gear.D);
        double nextAbsPressure =
                controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);

        assertEquals(90.0, nextAbsPressure);
    }

    @Test
    void emergencyBrake_whenGearAllowsBraking_appliesMaximumPressureAndWarning() {
        controller.emergencyBrake(GearBox.Gear.D);

        assertEquals(100.0, controller.getPressure());
        assertTrue(controller.isWarningActive());
    }

    @Test
    void emergencyBrake_whenCalledAfterAbs_resetsNextAbsPulse() {
        controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);
        controller.emergencyBrake(GearBox.Gear.D);

        double nextAbsPressure =
                controller.apply(100.0, HIGH_SPEED_KMH, GearBox.Gear.D);

        assertEquals(90.0, nextAbsPressure);
    }

    @Test
    void apply_whenGearIsPark_rejectsBraking() {
        assertThrows(IllegalStateException.class,
                () -> controller.apply(25.0, STATIONARY_SPEED_KMH,
                        GearBox.Gear.P));
        assertEquals(0.0, controller.getPressure());
    }

    @Test
    void emergencyBrake_whenGearIsPark_rejectsBraking() {
        assertThrows(IllegalStateException.class,
                () -> controller.emergencyBrake(GearBox.Gear.P));
        assertEquals(0.0, controller.getPressure());
        assertFalse(controller.isWarningActive());
    }

    @ParameterizedTest
    @CsvSource({
            "-0.1",
            "NaN",
            "Infinity",
            "-Infinity"
    })
    void apply_whenPressureIsInvalid_throws(double invalidPressure) {
        assertThrows(IllegalArgumentException.class,
                () -> controller.apply(invalidPressure,
                        STATIONARY_SPEED_KMH, GearBox.Gear.D));
    }

    @Test
    void apply_whenSpeedIsInvalid_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.apply(25.0, Double.NaN, GearBox.Gear.D));
    }

    @Test
    void apply_whenGearIsNull_throws() {
        assertThrows(NullPointerException.class,
                () -> controller.apply(25.0, STATIONARY_SPEED_KMH, null));
    }

    @Test
    void emergencyBrake_whenGearIsNull_throws() {
        assertThrows(NullPointerException.class,
                () -> controller.emergencyBrake(null));
    }
}
