package com.galacticodyssey.galaxy.nebula;

import java.util.Random;

/**
 * An ionisation zone within a nebula volume, emitting light in a specific spectral line.
 */
public final class IonisationZone {

    public final float centreX;
    public final float centreY;
    public final float centreZ;
    public final float radius;
    public final float intensity;
    public final float[] emissionColor;

    public IonisationZone(float centreX, float centreY, float centreZ,
                          float radius, float intensity, float[] emissionColor) {
        this.centreX = centreX;
        this.centreY = centreY;
        this.centreZ = centreZ;
        this.radius = radius;
        this.intensity = intensity;
        this.emissionColor = emissionColor;
    }

    /**
     * Generates an ionisation zone with a random emission color from a spectral distribution.
     * 40% H-alpha red, 25% OIII green, 20% SII deep red, 15% NII orange.
     */
    public static IonisationZone generate(float cx, float cy, float cz, Random rng) {
        float radius = 0.1f + rng.nextFloat() * 0.4f;
        float intensity = 0.3f + rng.nextFloat() * 0.7f;
        float[] color = pickEmissionColor(rng);
        return new IonisationZone(cx, cy, cz, radius, intensity, color);
    }

    private static float[] pickEmissionColor(Random rng) {
        float roll = rng.nextFloat();
        if (roll < 0.40f) {
            // H-alpha red
            return new float[]{0.9f, 0.15f, 0.1f, 0.8f};
        } else if (roll < 0.65f) {
            // OIII green
            return new float[]{0.1f, 0.85f, 0.3f, 0.7f};
        } else if (roll < 0.85f) {
            // SII deep red
            return new float[]{0.7f, 0.05f, 0.05f, 0.75f};
        } else {
            // NII orange
            return new float[]{0.95f, 0.5f, 0.1f, 0.65f};
        }
    }
}
