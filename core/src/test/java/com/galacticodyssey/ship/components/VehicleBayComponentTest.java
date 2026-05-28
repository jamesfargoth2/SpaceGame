package com.galacticodyssey.ship.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleBayComponentTest {
    @Test
    void tracksStoredVehiclesAndUsedSlots() {
        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 3;
        bay.storedVehicleIds.add("rover_light");
        bay.storedVehicleIds.add("tank_medium");
        assertEquals(2, bay.storedVehicleIds.size());
        assertTrue(bay.triggerRadius > 0f);
        assertNotNull(bay.localRampSpawnPosition);
    }
}
