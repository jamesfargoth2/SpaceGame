package com.galacticodyssey.galaxy;

import com.galacticodyssey.planet.Planet;

public final class OrbitalSlot {
    public final int index;
    public final float orbitalRadius;
    public final float eccentricity;
    public final float orbitalPeriod;
    public final OrbitalZone zone;
    public Planet planet;

    public OrbitalSlot(int index, float orbitalRadius, float eccentricity, OrbitalZone zone) {
        this.index = index;
        this.orbitalRadius = orbitalRadius;
        this.eccentricity = eccentricity;
        this.orbitalPeriod = (float) Math.pow(orbitalRadius, 1.5);
        this.zone = zone;
    }
}
