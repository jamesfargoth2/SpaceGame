package com.galacticodyssey.planet.tectonic;

/** Result of querying the nearest plate boundary at a point. */
public final class BoundaryQuery {
    public final BoundaryType type;
    /** 0 at the boundary line, 1 at/beyond the boundary's influence radius. */
    public final float distanceNormalized;

    public BoundaryQuery(BoundaryType type, float distanceNormalized) {
        this.type = type;
        this.distanceNormalized = distanceNormalized;
    }
}
