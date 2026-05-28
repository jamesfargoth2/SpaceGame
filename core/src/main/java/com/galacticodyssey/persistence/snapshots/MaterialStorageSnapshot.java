package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializable snapshot of a {@link com.galacticodyssey.crafting.components.MaterialStorageComponent}.
 * Captures capacity limits and all stored material quantities.
 */
public class MaterialStorageSnapshot {
    public float maxWeight;
    public float maxVolume;
    public Map<String, Integer> quantities = new HashMap<>();

    /** No-arg constructor for JSON deserialization. */
    public MaterialStorageSnapshot() {}
}
