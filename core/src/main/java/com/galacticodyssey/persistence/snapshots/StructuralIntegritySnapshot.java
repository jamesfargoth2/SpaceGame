package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class StructuralIntegritySnapshot {
    public static class ZoneData {
        public String zoneName;
        public float integrity;
        public float pressure;
        public boolean isBreached;
        public float breachArea;
        public ZoneData() {}
    }
    public List<ZoneData> zones = new ArrayList<>();
    public StructuralIntegritySnapshot() {}
}
