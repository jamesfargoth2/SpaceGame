package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class ShipSubsystemsSnapshot {
    public static class Entry {
        public String type;
        public float health;
        public float maxHealth;
        public float empDisableTimer;
        public boolean destroyed;
        public Entry() {}
    }
    public final List<Entry> entries = new ArrayList<>();
    public ShipSubsystemsSnapshot() {}
}
