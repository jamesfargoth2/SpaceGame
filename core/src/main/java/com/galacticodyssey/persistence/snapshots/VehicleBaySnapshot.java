package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class VehicleBaySnapshot {
    public int capacity;
    public List<String> storedVehicleIds = new ArrayList<>();
    public float triggerRadius;
    public float rampX, rampY, rampZ;
}
