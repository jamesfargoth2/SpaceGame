package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

public class ShipLoadoutSnapshot {
    public float maxMass;
    public Map<String, String> installedModules = new HashMap<>();
}
