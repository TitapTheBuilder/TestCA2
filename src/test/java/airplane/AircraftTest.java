package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final double VALID_FUEL_AMOUNT = 50.0;
    private static final double ZERO_FUEL_AMOUNT = 0.0;
    private static final double NEGATIVE_FUEL_AMOUNT = -10.0;

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

    @AfterEach
    void tearDown() {
        reset(engine, fuelSystem, landingGear, navigationSystem);
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"GROUNDED", "LANDED"}, mode = EnumSource.Mode.EXCLUDE)
    void prepareForTakeoff_InvalidStatus_ThrowsFlightException(FlightStatus invalidStatus) {
        setStatus(invalidStatus);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(invalidStatus, aircraft.getStatus());
        verify(engine, times(0)).isOperational();
        verify(fuelSystem, times(0)).hasEnoughFuel();
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"GROUNDED", "LANDED"})
    void prepareForTakeoff_ValidStatusAndAllComponentsReady_ChangesStatusToReady(FlightStatus validStatus) {
        setStatus(validStatus);
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
    void prepareForTakeoff_EngineNotOperational_ThrowsFlightException() {
        when(engine.isOperational()).thenReturn(false);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(0)).hasEnoughFuel();
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_InsufficientFuel_ThrowsFlightException() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_RouteUnavailable_ThrowsFlightException() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"READY"}, mode = EnumSource.Mode.EXCLUDE)
    void takeOff_StatusNotReady_ThrowsFlightException(FlightStatus invalidStatus) {
        setStatus(invalidStatus);

        assertThrows(FlightException.class, () -> aircraft.takeOff());

        assertEquals(invalidStatus, aircraft.getStatus());
        verify(engine, times(0)).start();
        verify(landingGear, times(0)).retract();
    }

    @Test
    void takeOff_ReadyStatusAndEngineStarts_StartsEngineRetractsGearAndChangesStatusToInFlight() {
        setStatus(FlightStatus.READY);
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
    }

    @Test
    void takeOff_EngineStartThrowsException_ChangesStatusToAbortedAndThrowsFlightException() {
        setStatus(FlightStatus.READY);
        doThrow(new IllegalStateException("The engine is not operational.")).when(engine).start();

        assertThrows(FlightException.class, () -> aircraft.takeOff());

        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(landingGear, times(0)).retract();
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"IN_FLIGHT"}, mode = EnumSource.Mode.EXCLUDE)
    void cruise_StatusNotInFlight_ThrowsFlightException(FlightStatus invalidStatus) {
        setStatus(invalidStatus);

        assertThrows(FlightException.class, () -> aircraft.cruise(VALID_FUEL_AMOUNT));

        assertEquals(invalidStatus, aircraft.getStatus());
        verify(engine, times(0)).isOperational();
        verify(fuelSystem, times(0)).consumeFuel(any(Double.class));
    }

    @ParameterizedTest
    @ValueSource(doubles = {ZERO_FUEL_AMOUNT, NEGATIVE_FUEL_AMOUNT})
    void cruise_NonPositiveFuelAmount_ThrowsIllegalArgumentException(double invalidFuelAmount) {
        setStatus(FlightStatus.IN_FLIGHT);

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(invalidFuelAmount));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(0)).isOperational();
        verify(fuelSystem, times(0)).consumeFuel(any(Double.class));
    }

    @Test
    void cruise_EngineNotOperational_PerformsEmergencyLandingWithoutConsumingFuel() {
        setStatus(FlightStatus.IN_FLIGHT);
        when(engine.isOperational()).thenReturn(false);
        doNothing().when(landingGear).deploy();
        doNothing().when(engine).stop();

        aircraft.cruise(VALID_FUEL_AMOUNT);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(0)).consumeFuel(any(Double.class));
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void cruise_OperationalEngineAndSufficientFuel_ConsumesFuelAndRemainsInFlight() {
        setStatus(FlightStatus.IN_FLIGHT);
        when(engine.isOperational()).thenReturn(true);
        doNothing().when(fuelSystem).consumeFuel(any(Double.class));

        aircraft.cruise(VALID_FUEL_AMOUNT);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).consumeFuel(VALID_FUEL_AMOUNT);
    }

    @Test
    void cruise_FuelConsumptionThrowsException_PerformsEmergencyLandingAndThrowsFlightException() {
        setStatus(FlightStatus.IN_FLIGHT);
        when(engine.isOperational()).thenReturn(true);
        doThrow(new IllegalStateException("Not enough fuel."))
                .when(fuelSystem).consumeFuel(any(Double.class));
        doNothing().when(landingGear).deploy();
        doNothing().when(engine).stop();

        assertThrows(FlightException.class, () -> aircraft.cruise(VALID_FUEL_AMOUNT));

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(VALID_FUEL_AMOUNT);
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"IN_FLIGHT"}, mode = EnumSource.Mode.EXCLUDE)
    void land_StatusNotInFlight_ThrowsFlightException(FlightStatus invalidStatus) {
        setStatus(invalidStatus);

        assertThrows(FlightException.class, () -> aircraft.land());

        assertEquals(invalidStatus, aircraft.getStatus());
        verify(landingGear, times(0)).deploy();
        verify(engine, times(0)).stop();
    }

    @Test
    void land_InFlightStatus_DeploysLandingGearStopsEngineAndChangesStatusToLanded() {
        setStatus(FlightStatus.IN_FLIGHT);
        doNothing().when(landingGear).deploy();
        doNothing().when(engine).stop();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"IN_FLIGHT"}, mode = EnumSource.Mode.EXCLUDE)
    void emergencyLand_StatusNotInFlight_ThrowsFlightException(FlightStatus invalidStatus) {
        setStatus(invalidStatus);

        assertThrows(FlightException.class, () -> aircraft.emergencyLand());

        assertEquals(invalidStatus, aircraft.getStatus());
        verify(landingGear, times(0)).deploy();
        verify(engine, times(0)).stop();
    }

    @Test
    void emergencyLand_InFlightStatus_DeploysLandingGearStopsEngineAndChangesStatusToLanded() {
        setStatus(FlightStatus.IN_FLIGHT);
        doNothing().when(landingGear).deploy();
        doNothing().when(engine).stop();

        aircraft.emergencyLand();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    private void setStatus(FlightStatus desiredStatus) {
        try {
            Field statusField = Aircraft.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(aircraft, desiredStatus);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set status via reflection", e);
        }
    }
}
