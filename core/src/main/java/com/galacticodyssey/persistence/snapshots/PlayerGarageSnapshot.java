package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class PlayerGarageSnapshot {
    public static class Entry {
        public String shipName;
        public long seed;
        public String sizeClass;
        public String acquiredVia;
        public Entry() {}
    }
    public final List<Entry> entries = new ArrayList<>();
    public PlayerGarageSnapshot() {}
}
