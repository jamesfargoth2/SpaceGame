package com.galacticodyssey.ship.boarding;

import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipSubsystemsComponentTest {

    @Test
    void newComponentHasAllSubsystemsOperational() {
        ShipSubsystemsComponent c = new ShipSubsystemsComponent();
        c.initDefaults(100f);
        assertTrue(c.enginesOperational());
        assertEquals(100f, c.get(SubsystemType.ENGINES).health, 0.001f);
        assertNotNull(c.get(SubsystemType.SHIELDS));
        assertNotNull(c.get(SubsystemType.WEAPONS));
        assertNotNull(c.get(SubsystemType.LIFE_SUPPORT));
    }

    @Test
    void enginesNotOperationalWhenHealthZero() {
        ShipSubsystemsComponent c = new ShipSubsystemsComponent();
        c.initDefaults(100f);
        c.get(SubsystemType.ENGINES).health = 0f;
        assertFalse(c.enginesOperational());
    }

    @Test
    void enginesNotOperationalWhileEmpTimerActive() {
        ShipSubsystemsComponent c = new ShipSubsystemsComponent();
        c.initDefaults(100f);
        c.get(SubsystemType.ENGINES).empDisableTimer = 3f;
        assertFalse(c.enginesOperational());
    }
}
