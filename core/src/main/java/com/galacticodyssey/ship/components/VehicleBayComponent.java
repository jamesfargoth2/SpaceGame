package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.VehicleBaySnapshot;
import java.util.ArrayList;
import java.util.List;

/** A ship's vehicle bay: stored vehicle definition ids plus deploy/retrieve geometry. */
public class VehicleBayComponent implements Component, Snapshotable<VehicleBaySnapshot> {
    public int capacity = 2;
    public final List<String> storedVehicleIds = new ArrayList<>();
    /** Ship-local point beside the ramp where deployed vehicles spawn. */
    public final Vector3 localRampSpawnPosition = new Vector3(0f, 0f, 6f);
    /** Proximity to the ramp within which a driven vehicle can be retrieved. */
    public float triggerRadius = 6f;

    @Override
    public VehicleBaySnapshot takeSnapshot() {
        VehicleBaySnapshot s = new VehicleBaySnapshot();
        s.capacity = capacity;
        s.storedVehicleIds = new ArrayList<>(storedVehicleIds);
        s.triggerRadius = triggerRadius;
        s.rampX = localRampSpawnPosition.x;
        s.rampY = localRampSpawnPosition.y;
        s.rampZ = localRampSpawnPosition.z;
        return s;
    }

    @Override
    public void restoreFromSnapshot(VehicleBaySnapshot s) {
        capacity = s.capacity;
        storedVehicleIds.clear();
        storedVehicleIds.addAll(s.storedVehicleIds);
        triggerRadius = s.triggerRadius;
        localRampSpawnPosition.set(s.rampX, s.rampY, s.rampZ);
    }
}
