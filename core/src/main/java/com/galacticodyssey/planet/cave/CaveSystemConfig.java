package com.galacticodyssey.planet.cave;

/** Input configuration for generating a cave system. */
public final class CaveSystemConfig {
    public final long seed;
    public final float systemDepthM;
    public final float systemRadiusM;
    public final int chamberCount;
    public final int tunnelCount;
    public final float collapseChance;
    public final boolean hasUndergroundLake;
    public final boolean hasLavaFlows;
    public final boolean hasBioluminescence;

    public CaveSystemConfig(long seed, float systemDepthM, float systemRadiusM,
                            int chamberCount, int tunnelCount, float collapseChance,
                            boolean hasUndergroundLake, boolean hasLavaFlows,
                            boolean hasBioluminescence) {
        this.seed = seed;
        this.systemDepthM = systemDepthM;
        this.systemRadiusM = systemRadiusM;
        this.chamberCount = chamberCount;
        this.tunnelCount = tunnelCount;
        this.collapseChance = collapseChance;
        this.hasUndergroundLake = hasUndergroundLake;
        this.hasLavaFlows = hasLavaFlows;
        this.hasBioluminescence = hasBioluminescence;
    }
}
