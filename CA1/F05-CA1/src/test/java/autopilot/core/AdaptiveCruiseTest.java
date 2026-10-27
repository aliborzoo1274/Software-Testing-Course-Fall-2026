package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdaptiveCruiseTest {

    private static final double TARGET_SPEED_KMH = 100.0;
    private static final double CRUISING_SPEED_KMH = 80.0;
    private static final double SAFE_DISTANCE_METERS = 40.0;
    private static final double EMERGENCY_DISTANCE_METERS = 20.0;
    private static final double RELEASE_DISTANCE_METERS = 48.0;

    private AdaptiveCruise cruise;

    @BeforeEach
    void setUp() {
        cruise = new AdaptiveCruise(TARGET_SPEED_KMH);
    }

    @ParameterizedTest(name = "speed={0}: safe distance={1}")
    @CsvSource({
            "0.0,    0.0",
            "40.0,  20.0",
            "80.0,  40.0",
            "120.0, 60.0"
    })
    void safeDistanceFor_whenGivenValidSpeed_returnsHalfSpeed(
            double speedKmh, double expectedDistanceMeters) {
        assertEquals(expectedDistanceMeters, cruise.safeDistanceFor(speedKmh));
    }

    @Test
    void update_whenDistanceIsClearlySafe_acceleratesByAtMostFiveKmh() {
        AdaptiveCruise.Decision decision =
                cruise.update(CRUISING_SPEED_KMH, SAFE_DISTANCE_METERS + 1.0);

        assertDecision(decision, AdaptiveCruise.Mode.ACCELERATE, 5.0);
    }

    @Test
    void update_whenNearTargetSpeed_acceleratesOnlyToTarget() {
        AdaptiveCruise.Decision decision =
                cruise.update(98.0, 50.0);

        assertDecision(decision, AdaptiveCruise.Mode.ACCELERATE, 2.0);
    }

    @Test
    void update_whenAtTargetSpeed_holdsSpeed() {
        AdaptiveCruise.Decision decision =
                cruise.update(TARGET_SPEED_KMH, 51.0);

        assertDecision(decision, AdaptiveCruise.Mode.HOLD, 0.0);
    }

    @Test
    void update_whenDistanceIsExactlySafe_holdsSpeed() {
        AdaptiveCruise.Decision decision =
                cruise.update(CRUISING_SPEED_KMH, SAFE_DISTANCE_METERS);

        assertDecision(decision, AdaptiveCruise.Mode.HOLD, 0.0);
    }

    @Test
    void update_whenDistanceIsBelowSafeDistance_appliesProportionalBraking() {
        AdaptiveCruise.Decision decision =
                cruise.update(CRUISING_SPEED_KMH, 30.0);

        assertDecision(decision, AdaptiveCruise.Mode.BRAKE, 25.0);
    }

    @ParameterizedTest(name = "distance={0}: emergency braking")
    @ValueSource(doubles = {0.0, EMERGENCY_DISTANCE_METERS})
    void update_whenDistanceIsAtOrBelowEmergencyThreshold_emergencyBrakes(
            double distanceMeters) {
        AdaptiveCruise.Decision decision =
                cruise.update(CRUISING_SPEED_KMH, distanceMeters);

        assertDecision(decision, AdaptiveCruise.Mode.EMERGENCY_BRAKE, 100.0);
    }

    @Test
    void update_whenBrakingAndDistanceHasNotReachedReleaseThreshold_continuesBraking() {
        cruise.update(CRUISING_SPEED_KMH, 30.0);

        AdaptiveCruise.Decision decision =
                cruise.update(CRUISING_SPEED_KMH, 42.0);

        assertDecision(decision, AdaptiveCruise.Mode.BRAKE, 10.0);
    }

    @Test
    void update_whenBrakingAndDistanceReachesReleaseThreshold_releasesBraking() {
        cruise.update(CRUISING_SPEED_KMH, 30.0);

        AdaptiveCruise.Decision decision =
                cruise.update(CRUISING_SPEED_KMH, RELEASE_DISTANCE_METERS);

        assertDecision(decision, AdaptiveCruise.Mode.ACCELERATE, 5.0);
    }

    @ParameterizedTest
    @CsvSource({
            "-0.1",
            "NaN",
            "Infinity",
            "-Infinity"
    })
    void safeDistanceFor_whenSpeedIsInvalid_throws(double invalidSpeed) {
        assertThrows(IllegalArgumentException.class,
                () -> cruise.safeDistanceFor(invalidSpeed));
    }

    @Test
    void update_whenDistanceIsNegative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> cruise.update(CRUISING_SPEED_KMH, -0.1));
    }

    @Test
    void update_whenSpeedIsInvalid_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> cruise.update(Double.NaN, SAFE_DISTANCE_METERS));
    }

    private static void assertDecision(AdaptiveCruise.Decision decision,
                                       AdaptiveCruise.Mode expectedMode,
                                       double expectedAmount) {
        assertEquals(expectedMode, decision.mode());
        assertEquals(expectedAmount, decision.amount());
    }
}
