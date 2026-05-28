package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;
import com.galacticodyssey.ship.boarding.systems.ShipSubsystemSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipSubsystemSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity ship;
    private ShipSubsystemsComponent subsystems;
    private final List<SubsystemDisabledEvent> disabled = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ShipSubsystemSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        ship.add(tc);

        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 500f;
        data.currentHullHp = 500f;
        ship.add(data);

        subsystems = new ShipSubsystemsComponent();
        subsystems.initDefaults(100f);
        ship.add(subsystems);

        engine.addEntity(ship);
        eventBus.subscribe(SubsystemDisabledEvent.class, disabled::add);
    }

    @Test
    void kineticAftHitDamagesEnginesAndHull() {
        eventBus.publish(new ShipDamageEvent(ship, null, 40f,
            DamageType.BALLISTIC, new Vector3(0, 0, -10)));
        engine.update(0.016f);
        assertEquals(60f, subsystems.get(SubsystemType.ENGINES).health, 0.01f);
        assertEquals(460f, ship.getComponent(ShipDataComponent.class).currentHullHp, 0.01f);
        assertTrue(disabled.isEmpty());
    }

    @Test
    void destroyingEnginesPublishesDisabledEvent() {
        eventBus.publish(new ShipDamageEvent(ship, null, 120f,
            DamageType.BALLISTIC, new Vector3(0, 0, -10)));
        engine.update(0.016f);
        assertEquals(0f, subsystems.get(SubsystemType.ENGINES).health, 0.01f);
        assertFalse(subsystems.enginesOperational());
        assertEquals(1, disabled.size());
        assertEquals(SubsystemType.ENGINES, disabled.get(0).subsystem);
    }

    @Test
    void empAftHitDisablesEnginesWithoutDestroying() {
        eventBus.publish(new ShipDamageEvent(ship, null, 30f,
            DamageType.EMP, new Vector3(0, 0, -10)));
        engine.update(0.016f);
        assertEquals(100f, subsystems.get(SubsystemType.ENGINES).health, 0.01f,
            "EMP must not reduce health");
        assertTrue(subsystems.get(SubsystemType.ENGINES).empDisableTimer > 0f);
        assertFalse(subsystems.enginesOperational());
        assertEquals(1, disabled.size());
    }

    @Test
    void empTimerRecoversOverTime() {
        eventBus.publish(new ShipDamageEvent(ship, null, 1f,
            DamageType.EMP, new Vector3(0, 0, -10)));
        engine.update(0.016f); // applies EMP, sets timer
        float timer = subsystems.get(SubsystemType.ENGINES).empDisableTimer;
        assertTrue(timer > 0f);
        // Advance well past the timer.
        engine.update(timer + 1f);
        assertTrue(subsystems.enginesOperational(), "engines recover after EMP expires");
    }

    @Test
    void hitRoutingRespectsShipRotation() {
        // Yaw the ship 180° about Y: its aft (local -Z) now points to world +Z.
        // A hit at world +Z must therefore route to ENGINES, not WEAPONS.
        ship.getComponent(TransformComponent.class).rotation.setFromAxis(0f, 1f, 0f, 180f);
        eventBus.publish(new ShipDamageEvent(ship, null, 120f,
            DamageType.BALLISTIC, new Vector3(0, 0, 10)));
        engine.update(0.016f);
        assertEquals(0f, subsystems.get(SubsystemType.ENGINES).health, 0.01f,
            "world +Z hit on a 180°-yawed ship is an aft (engines) hit");
        assertEquals(1, disabled.size());
        assertEquals(SubsystemType.ENGINES, disabled.get(0).subsystem);
    }
}
