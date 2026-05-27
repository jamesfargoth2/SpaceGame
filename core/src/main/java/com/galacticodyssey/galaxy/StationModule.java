package com.galacticodyssey.galaxy;

public final class StationModule {
    public final StationModuleType type;
    public final int level;
    public final int sectorIndex;

    public StationModule(StationModuleType type, int level, int sectorIndex) {
        this.type = type;
        this.level = level;
        this.sectorIndex = sectorIndex;
    }
}
