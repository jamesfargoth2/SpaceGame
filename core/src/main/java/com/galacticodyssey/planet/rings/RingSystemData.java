package com.galacticodyssey.planet.rings;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public final class RingSystemData {
    public float innerRadiusKm;
    public float outerRadiusKm;
    public float tiltDeg;
    public float thicknessKm;
    public Array<RingBand> bands;
    public Array<ShepherdMoon> shepherdMoons;
    public float rocheLimit;
    public long seed;

    /** Approximate impact probability per second for a ship traversing a band at given speed (m/s) and cross-section (m²). */
    public float traversalHazard(RingBand band, float shipSpeedMs, float shipCrossSectionM2) {
        float particleDensity = band.density * 1e7f;
        float particleRadius  = 0.01f;
        float crossSection    = MathUtils.PI * particleRadius * particleRadius + shipCrossSectionM2;
        return particleDensity * crossSection * shipSpeedMs;
    }
}
