package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of a single {@link com.galacticodyssey.crafting.RefiningJob}.
 * Captures all fields needed to reconstruct a job during save/load.
 */
public class RefiningJobSnapshot {
    public String jobId;
    public String recipeId;
    public String state; // RefiningJobState enum name
    public float progress;
    public float totalTime;
    public Map<String, Integer> inputsConsumed = new HashMap<>();
    public List<OutputEntry> outputs = new ArrayList<>();

    /** No-arg constructor for JSON deserialization. */
    public RefiningJobSnapshot() {}

    /**
     * A single output entry within a refining job snapshot.
     */
    public static class OutputEntry {
        public String materialId;
        public int quantity;

        /** No-arg constructor for JSON deserialization. */
        public OutputEntry() {}

        public OutputEntry(String materialId, int quantity) {
            this.materialId = materialId;
            this.quantity = quantity;
        }
    }
}
