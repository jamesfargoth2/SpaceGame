package com.galacticodyssey.galaxy.asteroid;

/**
 * Configuration parameters for procedural asteroid generation.
 */
public final class AsteroidConfig {

    public final long seed;
    public final AsteroidType type;
    public final float radiusKm;
    public final int craterCount;
    public final int flatCount;
    public final int veinCount;

    public AsteroidConfig(long seed, AsteroidType type, float radiusKm,
                          int craterCount, int flatCount, int veinCount) {
        this.seed = seed;
        this.type = type;
        this.radiusKm = radiusKm;
        this.craterCount = craterCount;
        this.flatCount = flatCount;
        this.veinCount = veinCount;
    }
}
