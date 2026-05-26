package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.events.ShipOverheatEvent;
import com.galacticodyssey.ship.weapons.systems.ShipHeatSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShipHeatSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity ship;
    private ShipWeaponHeatComponent heat;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new ShipHeatSystem(eventBus));

        ship = new Entity();
        heat = new ShipWeaponHeatComponent();
        heat.dissipationRate = 0.1f;
        heat.overheatThreshold = 0.5f;
        ship.add(heat);
        engine.addEntity(ship);
    }

    @Test
    void heatDissipates_overTime() {
        heat.heatPerHardpoint.put("turret_1", 0.8f);
        engine.update(1.0f);
        assertEquals(0.7f, heat.getHeat("turret_1"), 0.01f);
    }

    @Test
    void heatReachesMax_triggersOverheat() {
        AtomicReference<ShipOverheatEvent> received = new AtomicReference<>();
        eventBus.subscribe(ShipOverheatEvent.class, received::set);
        heat.heatPerHardpoint.put("turret_1", 1.0f);
        engine.update(0.016f);
        assertTrue(heat.isOverheated("turret_1"));
        assertNotNull(received.get());
    }

    @Test
    void heatDropsBelowThreshold_removesOverheat() {
        heat.overheatedHardpoints.add("turret_1");
        heat.heatPerHardpoint.put("turret_1", 0.4f);
        engine.update(1.0f);
        assertFalse(heat.isOverheated("turret_1"));
    }
}
