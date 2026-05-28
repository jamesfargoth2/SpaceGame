package com.galacticodyssey.planet.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleComponentsTest {
    @Test
    void tagHoldsDefinitionId() {
        VehicleTagComponent tag = new VehicleTagComponent();
        tag.definitionId = "rover_light";
        assertEquals("rover_light", tag.definitionId);
    }

    @Test
    void entryPointHasDefaultRadius() {
        VehicleEntryPointComponent entry = new VehicleEntryPointComponent();
        assertTrue(entry.triggerRadius > 0f);
        assertNotNull(entry.localExitOffset);
    }

    @Test
    void groundVehicleHasInputFieldsDefaultingToZero() {
        GroundVehicleComponent v = new GroundVehicleComponent();
        assertEquals(0f, v.throttleInput);
        assertEquals(0f, v.steerInput);
    }
}
