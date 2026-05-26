package com.galacticodyssey.galaxy.encounter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable weighted table of encounter types. */
public final class EncounterTable {

    private final Map<EncounterType, Float> weights;

    public EncounterTable(Map<EncounterType, Float> weights) {
        this.weights = Collections.unmodifiableMap(new HashMap<>(weights));
    }

    /** Returns the weight for the given encounter type, or 0 if absent. */
    public float getWeight(EncounterType type) {
        return weights.getOrDefault(type, 0f);
    }

    /** Returns all encounter weights (unmodifiable). */
    public Map<EncounterType, Float> getWeights() {
        return weights;
    }
}
