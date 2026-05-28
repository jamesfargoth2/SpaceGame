package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of a {@link com.galacticodyssey.crafting.components.RefineryComponent}.
 * Captures refinery configuration and all queued/active jobs.
 */
public class RefinerySnapshot {
    public int tier;
    public int maxQueueSize;
    public float speedMultiplier;
    public float powerCostPerSecond;
    public List<RefiningJobSnapshot> jobs = new ArrayList<>();

    /** No-arg constructor for JSON deserialization. */
    public RefinerySnapshot() {}
}
