package com.galacticodyssey.galaxy;

public enum StartingRegion {
    CORE(GalaxyRegion.CORE, 0.07),
    INNER_RIM(GalaxyRegion.INNER_RIM, 0.275),
    FRONTIER(GalaxyRegion.OUTER_RIM, 0.65);

    public final GalaxyRegion galaxyRegion;
    /** Fraction of galaxy radius used to position the initial chunk-load view. */
    public final double normalizedRadius;

    StartingRegion(GalaxyRegion galaxyRegion, double normalizedRadius) {
        this.galaxyRegion = galaxyRegion;
        this.normalizedRadius = normalizedRadius;
    }
}
