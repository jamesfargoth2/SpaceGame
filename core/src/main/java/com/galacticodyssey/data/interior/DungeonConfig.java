package com.galacticodyssey.data.interior;

/**
 * Input configuration for dungeon interior generation.
 */
public final class DungeonConfig {

    public final long seed;
    public final float boundsWidth;
    public final float boundsHeight;
    public final int maxBSPDepth;
    public final float minRoomSize;
    public final float extraCorridorFraction;
    public final int gateCount;

    public DungeonConfig(long seed, float boundsWidth, float boundsHeight,
                         int maxBSPDepth, float minRoomSize,
                         float extraCorridorFraction, int gateCount) {
        this.seed = seed;
        this.boundsWidth = boundsWidth;
        this.boundsHeight = boundsHeight;
        this.maxBSPDepth = maxBSPDepth;
        this.minRoomSize = minRoomSize;
        this.extraCorridorFraction = extraCorridorFraction;
        this.gateCount = gateCount;
    }
}
