package com.galacticodyssey.galaxy;

import java.util.EnumSet;
import java.util.List;

public final class DerelictWreck {
    public final long seed;
    public final int hullClass;
    public final float damageLevel;
    public final List<String> remainingModules;
    public final EnumSet<WreckHazard> hazards;
    public final int lootTier;
    public final List<String> logEntries;
    public final DerelictCause cause;

    public DerelictWreck(long seed, int hullClass, float damageLevel, List<String> remainingModules,
                         EnumSet<WreckHazard> hazards, int lootTier, List<String> logEntries,
                         DerelictCause cause) {
        this.seed = seed;
        this.hullClass = hullClass;
        this.damageLevel = damageLevel;
        this.remainingModules = List.copyOf(remainingModules);
        this.hazards = hazards;
        this.lootTier = lootTier;
        this.logEntries = List.copyOf(logEntries);
        this.cause = cause;
    }
}
