package com.galacticodyssey.ship;

/** Selects which hull mesh generation path a {@link HullStyle} uses. */
public enum GeneratorType {
    /** Smooth lofted spline hull (existing {@link ShipHullGenerator}). */
    LOFTED,
    /** Flat-shaded faceted / crystalline hull (FacetedHullGenerator, added in plan 1c). */
    FACETED
}
