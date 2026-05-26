package com.galacticodyssey.galaxy.station;

/**
 * Input configuration for space station generation.
 */
public final class StationConfig {

    public final long seed;
    public final StationType stationType;
    public final String factionId;
    public final boolean hasRingSection;

    public StationConfig(long seed, StationType stationType,
                         String factionId, boolean hasRingSection) {
        this.seed = seed;
        this.stationType = stationType;
        this.factionId = factionId;
        this.hasRingSection = hasRingSection;
    }
}
