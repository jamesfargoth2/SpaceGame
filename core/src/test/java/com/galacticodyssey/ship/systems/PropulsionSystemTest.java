package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.PropulsionUtil;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.events.FuelDepletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PropulsionSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity ship;
    private EngineSpecComponent engineSpec;
    private FuelTankComponent fuel;
    private ShipFlightComponent flight;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new PropulsionSystem(eventBus));

        ship = new Entity();
        engineSpec = new EngineSpecComponent();
        engineSpec.isp = 350f;
        engineSpec.maxThrust = 50000f;
        engineSpec.throttleResponseRate = 1000f; // near-instant for most tests
        engineSpec.canThrottleOff = true;

        fuel = new FuelTankComponent();
        fuel.maxMass = 1000f;
        fuel.currentMass = 1000f;

        flight = new ShipFlightComponent();
        flight.currentThrottle = 0f;

        ship.add(engineSpec);
        ship.add(fuel);
        ship.add(flight);
        engine.addEntity(ship);
    }

    @Test
    void thrustConsumesFuel() {
        flight.currentThrottle = 1f;
        float dt = 1f / 60f;
        for (int i = 0; i < 60; i++) {
            engine.update(dt);
        }
        assertTrue(fuel.currentMass < 1000f, "Fuel should have decreased after thrusting");
        assertTrue(fuel.currentMass > 0f, "Fuel should not be fully depleted after 1 second");
    }

    @Test
    void noFuelNoThrust() {
        fuel.currentMass = 0f;
        flight.currentThrottle = 1f;
        engine.update(1f / 60f);
        assertEquals(0f, engineSpec.actualThrust, "Thrust should be zero with no fuel");
    }

    @Test
    void throttleResponseLags() {
        engineSpec.throttleResponseRate = 2f; // slow response
        engineSpec.currentThrottle = 0f;
        flight.currentThrottle = 1f;

        engine.update(1f / 60f);
        assertTrue(engineSpec.currentThrottle > 0f, "Throttle should have started increasing");
        assertTrue(engineSpec.currentThrottle < 0.5f,
            "Throttle should not have reached halfway in one frame at rate=2");
    }

    @Test
    void fuelDepletedEventPublished() {
        AtomicBoolean fired = new AtomicBoolean(false);
        AtomicReference<Entity> eventEntity = new AtomicReference<>();
        eventBus.subscribe(FuelDepletedEvent.class, e -> {
            fired.set(true);
            eventEntity.set(e.shipEntity);
        });

        fuel.currentMass = 1f; // tiny amount of fuel
        flight.currentThrottle = 1f;

        // Run until fuel runs out
        float dt = 1f / 60f;
        for (int i = 0; i < 600; i++) {
            engine.update(dt);
            if (fired.get()) break;
        }

        assertTrue(fired.get(), "FuelDepletedEvent should have been published");
        assertSame(ship, eventEntity.get(), "Event should reference the ship entity");
        assertEquals(0f, fuel.currentMass, "Fuel should be exactly 0");
    }

    @Test
    void deltaVBudgetCalculation() {
        float dv = PropulsionUtil.deltaVBudget(300f, 10000f, 5000f);
        assertEquals(2038.6f, dv, 5f);
    }

    @Test
    void partialFuelScalesThrust() {
        // Set fuel so low it can't sustain full thrust for one tick
        float dt = 1f / 60f;
        float fullThrust = engineSpec.maxThrust;
        float fullFlowRate = PropulsionUtil.massFlowRate(fullThrust, engineSpec.isp);
        float fullPropNeeded = fullFlowRate * dt;

        // Give exactly half the needed propellant
        fuel.currentMass = fullPropNeeded * 0.5f;
        flight.currentThrottle = 1f;

        engine.update(dt);

        // Thrust should be roughly half of max (accounting for throttle response)
        assertTrue(engineSpec.actualThrust < fullThrust,
            "Thrust should be less than max with insufficient fuel");
        assertTrue(engineSpec.actualThrust > 0f,
            "Thrust should still be positive with some fuel");
        assertEquals(0f, fuel.currentMass, 0.001f,
            "All remaining fuel should have been consumed");
    }
}
