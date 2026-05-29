package com.galacticodyssey.city.layout.model;

/** Double-precision galaxy/planet placement metadata. Sub-project A leaves this
 *  unassigned; sub-project E fills it when projecting the local layout onto a planet. */
public final class GalaxyAnchor {
    public boolean assigned = false;
    public double galaxyX, galaxyY, galaxyZ; // floating-origin galaxy coords
    public double latitudeDeg, longitudeDeg; // surface position
    public float  headingDeg;                // local-frame orientation
}
