package autopilot.core;

import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoDriverTest {

    private static final double CRUISING_SPEED_KMH = 80.0;
    private static final double CLEAR_OBSTACLE_DISTANCE_METERS = 100.0;
    private static final double CENTERED_LANE_OFFSET_METERS = 0.0;
    private static final double POSITIVE_LANE_OFFSET_METERS = 1.0;
    private static final double NEGATIVE_LANE_OFFSET_METERS = -1.0;
    private static final double NO_BRAKE_PRESSURE = 0.0;
    private static final double SAFE_MODE_BRAKE_PRESSURE = 30.0;
    private static final double CENTERED_STEERING_ANGLE_DEGREES = 0.0;
    private static final String SPEED_SENSOR_FAILURE_MESSAGE = "Speed sensor failed";
    private static final String OBSTACLE_SENSOR_FAILURE_MESSAGE = "Obstacle sensor failed";

    private SpeedSensor speedSensor;
    private ObstacleSensor obstacleSensor;
    private LaneSensor laneSensor;

    @BeforeEach
    void setUp() {
        speedSensor = () -> CRUISING_SPEED_KMH;
        obstacleSensor = () -> CLEAR_OBSTACLE_DISTANCE_METERS;
        laneSensor = () -> CENTERED_LANE_OFFSET_METERS;
    }

    @Test
    void tick_whenSensorsAreHealthy_keepsNormalDrivingState() {
        AutoDriver.DriveResult result = newAutoDriver().tick(GearBox.Gear.D);

        assertAll("Normal driving result",
                () -> assertFalse(result.safeMode(), "Safe mode should be off"),
                () -> assertNotNull(result.cruiseMode(), "Cruise mode should be set"),
                () -> assertEquals(NO_BRAKE_PRESSURE, result.brakePressure(),
                        "Braking should not be applied")
        );
    }

    @Test
    void tick_whenLaneOffsetIsPositive_steersInNegativeDirection() {
        laneSensor = () -> POSITIVE_LANE_OFFSET_METERS;

        AutoDriver.DriveResult result = newAutoDriver().tick(GearBox.Gear.D);

        assertTrue(result.steeringAngleDegrees() < CENTERED_STEERING_ANGLE_DEGREES,
                "Positive lane offset should cause negative steering");
    }

    @Test
    void tick_whenLaneOffsetIsNegative_steersInPositiveDirection() {
        laneSensor = () -> NEGATIVE_LANE_OFFSET_METERS;

        AutoDriver.DriveResult result = newAutoDriver().tick(GearBox.Gear.D);

        assertTrue(result.steeringAngleDegrees() > CENTERED_STEERING_ANGLE_DEGREES,
                "Negative lane offset should cause positive steering");
    }

    @Test
    void tick_whenSpeedSensorFails_entersSafeMode() {
        speedSensor = () -> {
            throw new SensorFailureException(SPEED_SENSOR_FAILURE_MESSAGE);
        };

        AutoDriver.DriveResult result = newAutoDriver().tick(GearBox.Gear.D);

        assertSafeModeResult(result);
    }

    @Test
    void tick_whenObstacleSensorFails_entersSafeMode() {
        obstacleSensor = () -> {
            throw new SensorFailureException(OBSTACLE_SENSOR_FAILURE_MESSAGE);
        };

        AutoDriver.DriveResult result = newAutoDriver().tick(GearBox.Gear.D);

        assertSafeModeResult(result);
    }

    private AutoDriver newAutoDriver() {
        return new AutoDriver(speedSensor, obstacleSensor, laneSensor, CRUISING_SPEED_KMH);
    }

    private static void assertSafeModeResult(AutoDriver.DriveResult result) {
        // These assertions describe different aspects of one safety response.
        assertAll("Safe-mode result",
                () -> assertTrue(result.safeMode(), "Safe mode should be enabled"),
                () -> assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure(),
                        "Safe mode should apply the expected brake pressure"),
                () -> assertEquals(CENTERED_STEERING_ANGLE_DEGREES, result.steeringAngleDegrees(),
                        "Steering should be centered"),
                () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode(),
                        "Cruise controller should be in BRAKE mode"),
                () -> assertFalse(result.warningActive(),
                        "Warning flag should match the existing safe-mode contract")
        );
    }
}
