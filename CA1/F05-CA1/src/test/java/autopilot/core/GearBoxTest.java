package autopilot.core;

import autopilot.exception.IllegalGearChangeException;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import autopilot.core.GearBox.Gear;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GearBoxTest {

    private static final double REVERSE_ENTRY_LIMIT_KMH = 5.0;
    private static final double MOVING_SPEED_KMH = 20.0;

    private double speedKmh;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        speedKmh = 0.0;
        SpeedSensor sensor = () -> speedKmh;
        gearBox = new GearBox(sensor);
    }

    @Test
    void changeTo_whenParkToReverseAtZero_selectsReverse() {
        assertAllowedTransition(Gear.P, Gear.R, 0.0);
    }

    @Test
    void changeTo_whenParkToReverseJustBelowLimit_selectsReverse() {
        assertAllowedTransition(Gear.P, Gear.R,
                Math.nextDown(REVERSE_ENTRY_LIMIT_KMH));
    }

    @Test
    void changeTo_whenParkToReverseAtLimit_rejectsTransition() {
        assertRejectedTransition(Gear.P, Gear.R, REVERSE_ENTRY_LIMIT_KMH);
    }

    @Test
    void changeTo_whenParkToReverseAboveLimit_rejectsTransition() {
        assertRejectedTransition(Gear.P, Gear.R,
                Math.nextUp(REVERSE_ENTRY_LIMIT_KMH));
    }

    @Test
    void changeTo_whenReverseToDriveAtZero_selectsDrive() {
        assertAllowedTransition(Gear.R, Gear.D, 0.0);
    }

    @Test
    void changeTo_whenReverseToDriveWhileMoving_rejectsTransition() {
        assertRejectedTransition(Gear.R, Gear.D, Double.MIN_VALUE);
    }

    @Test
    void changeTo_whenDriveToReverseAtZero_selectsReverse() {
        assertAllowedTransition(Gear.D, Gear.R, 0.0);
    }

    @Test
    void changeTo_whenDriveToReverseWhileMoving_rejectsTransition() {
        assertRejectedTransition(Gear.D, Gear.R, Double.MIN_VALUE);
    }

    @Test
    void changeTo_whenReverseToNeutralWhileMoving_selectsNeutral() {
        assertAllowedTransition(Gear.R, Gear.N, MOVING_SPEED_KMH);
    }

    @Test
    void changeTo_whenNeutralToDriveWhileMoving_selectsDrive() {
        assertAllowedTransition(Gear.N, Gear.D, MOVING_SPEED_KMH);
    }

    @ParameterizedTest
    @EnumSource(Gear.class)
    void changeTo_whenTargetIsCurrentGear_keepsCurrentGear(Gear currentGear) {
        moveToGear(currentGear);
        speedKmh = Double.NaN; // Same-gear request returns before reading the sensor.

        gearBox.changeTo(currentGear);

        assertEquals(currentGear, gearBox.getCurrentGear());
    }

    @Test
    void changeTo_whenParkToNeutral_rejectsTransition() {
        assertRejectedTransition(Gear.P, Gear.N, 0.0);
    }

    @Test
    void changeTo_whenParkToDrive_rejectsTransition() {
        assertRejectedTransition(Gear.P, Gear.D, 0.0);
    }

    @Test
    void changeTo_whenReverseToPark_rejectsTransition() {
        assertRejectedTransition(Gear.R, Gear.P, 0.0);
    }

    @Test
    void changeTo_whenNeutralToPark_rejectsTransition() {
        assertRejectedTransition(Gear.N, Gear.P, 0.0);
    }

    @Test
    void changeTo_whenNeutralToReverse_rejectsTransition() {
        assertRejectedTransition(Gear.N, Gear.R, 0.0);
    }

    @Test
    void changeTo_whenDriveToPark_rejectsTransition() {
        assertRejectedTransition(Gear.D, Gear.P, 0.0);
    }

    @Test
    void changeTo_whenDriveToNeutral_rejectsTransition() {
        assertRejectedTransition(Gear.D, Gear.N, 0.0);
    }

    @Test
    void changeTo_whenTargetIsNull_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> gearBox.changeTo(null));
        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @Test
    void changeTo_whenSpeedIsNegative_rejectsReading() {
        assertInvalidSpeed(-0.1);
    }

    @Test
    void changeTo_whenSpeedIsNaN_rejectsReading() {
        assertInvalidSpeed(Double.NaN);
    }

    @Test
    void changeTo_whenSpeedIsPositiveInfinity_rejectsReading() {
        assertInvalidSpeed(Double.POSITIVE_INFINITY);
    }

    @Test
    void changeTo_whenSpeedIsNegativeInfinity_rejectsReading() {
        assertInvalidSpeed(Double.NEGATIVE_INFINITY);
    }

    private void assertAllowedTransition(Gear from, Gear to, double speed) {
        moveToGear(from);
        speedKmh = speed;

        gearBox.changeTo(to);

        assertEquals(to, gearBox.getCurrentGear());
    }

    private void assertRejectedTransition(Gear from, Gear to, double speed) {
        moveToGear(from);
        speedKmh = speed;

        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(to));
        assertEquals(from, gearBox.getCurrentGear());
    }

    private void assertInvalidSpeed(double invalidSpeed) {
        speedKmh = invalidSpeed;

        assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(Gear.R));
        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    private void moveToGear(Gear target) {
        // Every fixture starts in Park with a valid zero-speed reading.
        switch (target) {
            case P -> { /* Already in Park */ }
            case R -> gearBox.changeTo(Gear.R);
            case N -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.N);
            }
            case D -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.N);
                gearBox.changeTo(Gear.D);
            }
        }
    }
}
