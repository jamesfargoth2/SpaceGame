package com.galacticodyssey.ship.components;

import com.galacticodyssey.persistence.snapshots.VehicleBaySnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleBaySnapshotTest {
    @Test
    void roundTripsStoredVehicles() {
        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 3;
        bay.storedVehicleIds.add("rover_light");
        bay.storedVehicleIds.add("tank_medium");
        bay.localRampSpawnPosition.set(1, 2, 3);

        VehicleBaySnapshot s = bay.takeSnapshot();
        VehicleBayComponent restored = new VehicleBayComponent();
        restored.restoreFromSnapshot(s);

        assertEquals(3, restored.capacity);
        assertEquals(2, restored.storedVehicleIds.size());
        assertEquals("tank_medium", restored.storedVehicleIds.get(1));
        assertEquals(3f, restored.localRampSpawnPosition.z, 1e-4);
    }
}
