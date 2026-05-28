package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;

public final class Moon {
    public final long seed;
    public final PlanetType type;
    public final float radius;
    public final float mass;
    public final float surfaceGravity;

    public final float orbitalRadius;
    public final float orbitalEccentricity;
    public final float orbitalInclination;
    public float orbitalPeriod;

    public float currentTrueAnomaly;
    public float currentMeanAnomaly;

    public Moon(long seed, PlanetType type, float radius, float mass) {
        this(seed, type, radius, mass, 0f, 0f, 0f);
    }

    public Moon(long seed, PlanetType type, float radius, float mass,
                float orbitalRadius, float orbitalEccentricity, float orbitalInclination) {
        this.seed = seed;
        this.type = type;
        this.radius = radius;
        this.mass = mass;
        this.surfaceGravity = mass / (radius * radius);
        this.orbitalRadius = orbitalRadius;
        this.orbitalEccentricity = orbitalEccentricity;
        this.orbitalInclination = orbitalInclination;
    }

    public void computeOrbitalPeriod(float parentMassKg) {
        if (orbitalRadius <= 0f || parentMassKg <= 0f) {
            orbitalPeriod = Float.MAX_VALUE;
            return;
        }
        float GM = OrbitalConstants.G * parentMassKg;
        float radiusGameUnits = orbitalRadius * OrbitalConstants.AU_TO_GAME_UNITS;
        orbitalPeriod = OrbitalMechanics.orbitalPeriod(GM, radiusGameUnits);
    }
}
