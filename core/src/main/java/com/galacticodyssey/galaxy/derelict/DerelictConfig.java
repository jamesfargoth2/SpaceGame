package com.galacticodyssey.galaxy.derelict;

/** Input configuration for generating a derelict wreck. */
public final class DerelictConfig {
    public final long seed;
    public final WreckType wreckType;
    public final HullClass originalHullClass;
    public final float ageYears;
    /** Fraction of sections expected to remain intact, 0-1. */
    public final float intactFraction;
    public final boolean hasScavengers;
    public final boolean hasSurvivor;

    public DerelictConfig(long seed, WreckType wreckType, HullClass originalHullClass,
                          float ageYears, float intactFraction,
                          boolean hasScavengers, boolean hasSurvivor) {
        this.seed = seed;
        this.wreckType = wreckType;
        this.originalHullClass = originalHullClass;
        this.ageYears = ageYears;
        this.intactFraction = intactFraction;
        this.hasScavengers = hasScavengers;
        this.hasSurvivor = hasSurvivor;
    }
}
