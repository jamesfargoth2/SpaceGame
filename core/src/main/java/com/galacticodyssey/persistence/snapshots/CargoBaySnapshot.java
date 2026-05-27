package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

public class CargoBaySnapshot {
    public float capacity;
    public Map<String, Integer> contents = new HashMap<>();
    public float usedVolume;
    public CargoBaySnapshot() {}
}
