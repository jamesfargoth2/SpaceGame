package com.galacticodyssey.planet.climate;

import com.galacticodyssey.galaxy.GalaxyNoise;

/**
 * Global atmospheric circulation model based on three-cell system:
 * Hadley (0-30), Ferrel (30-60), Polar (60-90). Tidally-locked
 * planets use radial wind from the substellar point instead.
 */
public final class WindModel {

    private WindModel() {}

    public static void compute(float[] windU, float[] windV,
                               int lonSteps, int latSteps,
                               boolean tidallyLocked, GalaxyNoise perturbation) {
        for (int lat = 0; lat < latSteps; lat++) {
            float latRad = ((float) lat / (latSteps - 1) - 0.5f) * (float) Math.PI;
            float latDeg = Math.abs(latRad * 180f / (float) Math.PI);

            for (int lon = 0; lon < lonSteps; lon++) {
                float lonRad = ((float) lon / lonSteps) * 2f * (float) Math.PI - (float) Math.PI;
                int idx = lat * lonSteps + lon;

                float u, v;
                if (tidallyLocked) {
                    float dx = -lonRad;
                    float dy = -latRad;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0.01f) {
                        u = dx / len * 0.5f;
                        v = dy / len * 0.5f;
                    } else {
                        u = 0f;
                        v = 0f;
                    }
                } else {
                    float sign = latRad >= 0 ? 1f : -1f;
                    if (latDeg < 30f) {
                        // Hadley cell: surface toward equator, easterly (trade winds)
                        u = -0.4f;
                        v = -sign * 0.3f;
                    } else if (latDeg < 60f) {
                        // Ferrel cell: surface toward poles, westerly
                        u = 0.5f;
                        v = sign * 0.2f;
                    } else {
                        // Polar cell: surface toward equator, easterly
                        u = -0.3f;
                        v = -sign * 0.2f;
                    }
                }

                float noise = perturbation.fbm(lonRad * 3f, latRad * 3f, 4, 0.5f, 2.0f);
                windU[idx] = u + noise * 0.15f;
                windV[idx] = v + noise * 0.1f;
            }
        }
    }
}
