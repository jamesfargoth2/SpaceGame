package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ShipGenerationConfigTest {

    @Test
    void defaultsAreIndependentAndPristine() {
        ShipGenerationConfig c = ShipGenerationConfig.defaults(123L, ShipSizeClass.SMALL);
        assertEquals(123L, c.seed);
        assertEquals(ShipSizeClass.SMALL, c.sizeClass);
        assertNull(c.faction);
        assertEquals(ShipRole.CIVILIAN, c.role);
        assertEquals(1.0f, c.conditionFactor, 1e-4);
        assertFalse(c.isFlagship);
    }

    @Test
    void fieldsAreSettable() {
        ShipGenerationConfig c = new ShipGenerationConfig();
        c.seed = 9L;
        c.sizeClass = ShipSizeClass.LARGE;
        c.role = ShipRole.WARSHIP;
        c.conditionFactor = 0.4f;
        c.isFlagship = true;
        assertEquals(ShipSizeClass.LARGE, c.sizeClass);
        assertEquals(ShipRole.WARSHIP, c.role);
        assertEquals(0.4f, c.conditionFactor, 1e-4);
    }
}
