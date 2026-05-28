package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFlightEnginesGuardTest {

    @Test
    void shipWithNoSubsystemsCanThrust() {
        Entity ship = new Entity();
        assertTrue(ShipFlightSystem.canThrust(ship));
    }

    @Test
    void shipWithOperationalEnginesCanThrust() {
        Entity ship = new Entity();
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        ship.add(subs);
        assertTrue(ShipFlightSystem.canThrust(ship));
    }

    @Test
    void shipWithDisabledEnginesCannotThrust() {
        Entity ship = new Entity();
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        subs.get(SubsystemType.ENGINES).empDisableTimer = 3f;
        ship.add(subs);
        assertFalse(ShipFlightSystem.canThrust(ship));
    }
}
