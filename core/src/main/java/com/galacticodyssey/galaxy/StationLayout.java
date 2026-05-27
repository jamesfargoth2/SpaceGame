package com.galacticodyssey.galaxy;

import java.util.List;

public final class StationLayout {
    public final long seed;
    public final StationType type;
    public final int tier;
    public final List<StationModule> modules;
    public final int dockingPorts;
    public final int populationCapacity;
    public final float defenseRating;
    public final String factionId;

    public StationLayout(long seed, StationType type, int tier, List<StationModule> modules,
                         int dockingPorts, int populationCapacity, float defenseRating, String factionId) {
        this.seed = seed;
        this.type = type;
        this.tier = tier;
        this.modules = List.copyOf(modules);
        this.dockingPorts = dockingPorts;
        this.populationCapacity = populationCapacity;
        this.defenseRating = defenseRating;
        this.factionId = factionId;
    }
}
