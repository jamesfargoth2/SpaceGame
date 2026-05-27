package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.Planet;

public final class OrbitalSlot {

    public final int index;
    public final float orbitalRadius;
    public final float eccentricity;
    public final float orbitalPeriod;
    public final OrbitalZone zone;
    public Planet planet;

    /** Mass of the parent star (kg, game scale). Set by whoever populates the slot. */
    public float starMass;

    /**
     * Live true anomaly (radians) advanced each tick by {@link KeplerianOrbitSystem}.
     * Initialised to 0 (periapsis); override after construction for a non-zero epoch position.
     */
    public float currentTrueAnomaly = 0f;

    /** Mean anomaly accumulator (radians); advanced internally by KeplerianOrbitSystem. */
    public float currentMeanAnomaly = 0f;

    public OrbitalSlot(int index, float orbitalRadius, float eccentricity, OrbitalZone zone) {
        this.index = index;
        this.orbitalRadius = orbitalRadius;
        this.eccentricity = eccentricity;
        this.orbitalPeriod = (float) Math.pow(orbitalRadius, 1.5);
        this.zone = zone;
    }

    /**
     * Returns the current world-space position of this body relative to the star centre.
     * Delegates to {@link OrbitalMechanics#orbitPosition} using a flat orbit (zero inclination
     * / node angles) matching the original 2-D procgen layout unless the caller has set up a
     * full {@link KeplerOrbit} elsewhere.
     *
     * @param starMassKg star mass in kg; pass {@link #starMass} or supply explicitly
     * @param out        output vector — reused and returned
     */
    public Vector3 getLocalPosition(float starMassKg, Vector3 out) {
        KeplerOrbit o = new KeplerOrbit();
        o.semiMajorAxis     = orbitalRadius;
        o.eccentricity      = eccentricity;
        o.inclination       = 0f;
        o.longitudeAscNode  = 0f;
        o.argumentPeriapsis = 0f;
        o.primaryMass       = starMassKg;
        o.GM                = OrbitalMechanics.G * starMassKg;
        return OrbitalMechanics.orbitPosition(o, currentTrueAnomaly, out);
    }

    /** Convenience overload that uses the stored {@link #starMass}. */
    public Vector3 getLocalPosition(Vector3 out) {
        return getLocalPosition(starMass, out);
    }
}
