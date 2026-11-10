package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AircraftTest {

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    @Test
    void land_whenAircraftIsInFlightAfterCruise_deploysGearStopsEngineAndSetsStatusToLanded() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();
        doNothing().when(fuelSystem).consumeFuel(any(Double.class));
        doNothing().when(landingGear).deploy();
        doNothing().when(engine).stop();

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        aircraft.cruise(150.0);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        aircraft.land();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        verify(engine, times(2)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
        verify(fuelSystem, times(1)).consumeFuel(any(Double.class));
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void prepareForTakeoff_whenInsufficientFuelThenResetWithValidComponents_throwsExceptionThenSetsStatusToReady() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, times(0)).isRouteAvailable();

        reset(engine, fuelSystem, navigationSystem);

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_whenNavigationSystemThrowsException_propagatesFlightExceptionAndRemainsGrounded() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable())
                .thenThrow(new FlightException("Navigation sensor unreachable."));

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    @Test
    void cruise_whenEngineIsNotOperational_triggersEmergencyLandingWithoutConsumingFuel() {
        when(engine.isOperational()).thenReturn(true, false);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        aircraft.cruise(100.0);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(0)).consumeFuel(any(Double.class));
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void cruise_whenFuelConsumptionFails_triggersEmergencyLandingAndThrowsFlightException() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        doThrow(new IllegalStateException("Not enough fuel."))
                .when(fuelSystem).consumeFuel(any(Double.class));

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        FlightException exception = assertThrows(
                FlightException.class,
                () -> aircraft.cruise(500.0)
        );

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        assertSame(IllegalStateException.class, exception.getCause().getClass());
        verify(fuelSystem, times(1)).consumeFuel(any(Double.class));
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void takeOff_whenEngineStartThrowsException_setsStatusToAbortedAndThrowsFlightException() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        doThrow(new IllegalStateException("Starter motor failed.")).when(engine).start();

        aircraft.prepareForTakeoff();

        assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());

        verify(engine, times(1)).start();
        verify(landingGear, times(0)).retract();
    }

    @Test
    void cruise_whenAircraftNotInFlightOrFuelAmountNonPositive_throwsExceptionWithoutConsumingFuel() {
        assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertThrows(FlightException.class, () -> aircraft.cruise(50.0));
        assertThrows(FlightException.class, () -> aircraft.land());

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(0.0));
        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(-25.0));

        verify(fuelSystem, times(0)).consumeFuel(any(Double.class));
    }


    @Test
    void cruise_whenEngineOperational_consumesRequestedFuelAmount() {
        // Setup
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        // Exercise
        aircraft.cruise(150.0);

        // Verify
        verify(fuelSystem, times(1)).consumeFuel(150.0);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

}