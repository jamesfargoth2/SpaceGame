package com.galacticodyssey.planet.climate;

import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.HeightSampler;
import com.galacticodyssey.planet.Planet;

/**
 * Simulates planetary climate to derive temperature and precipitation grids.
 * Uses a three-step pipeline:
 * 1) Base temperature from solar flux, lapse rate, and ocean moderation
 * 2) Wind from global circulation cells (Hadley/Ferrel/Polar)
 * 3) Moisture advection along wind vectors with orographic precipitation
 */
public final class ClimateSimulator {

    private static final int LON_STEPS = 128;
    private static final int LAT_STEPS = 64;
    private static final float LAPSE_RATE = 6.5f; // K per km

    private final GalaxyNoise perturbation;

    public ClimateSimulator(long seed) {
        this.perturbation = new GalaxyNoise(seed);
    }

    public ClimateData simulate(Planet planet, Atmosphere atmosphere, HeightSampler heightSampler) {
        float surfaceTemp = atmosphere != null ? atmosphere.surfaceTemp : 200f;
        float maxElevationKm = planet.radius * 20f; // rough scale

        float[] heightGrid = new float[LON_STEPS * LAT_STEPS];
        float[] temperature = new float[LON_STEPS * LAT_STEPS];
        float[] windU = new float[LON_STEPS * LAT_STEPS];
        float[] windV = new float[LON_STEPS * LAT_STEPS];
        float[] precipitation = new float[LON_STEPS * LAT_STEPS];
        float[] moisture = new float[LON_STEPS * LAT_STEPS];

        // Sample height grid
        float seaLevel = 0f;
        for (int lat = 0; lat < LAT_STEPS; lat++) {
            float latRad = ((float) lat / (LAT_STEPS - 1) - 0.5f) * (float) Math.PI;
            for (int lon = 0; lon < LON_STEPS; lon++) {
                float lonRad = ((float) lon / LON_STEPS) * 2f * (float) Math.PI - (float) Math.PI;
                int idx = lat * LON_STEPS + lon;
                heightGrid[idx] = heightSampler.sample(lonRad, latRad);
            }
        }

        // Step 1: base temperature
        for (int lat = 0; lat < LAT_STEPS; lat++) {
            float latRad = ((float) lat / (LAT_STEPS - 1) - 0.5f) * (float) Math.PI;
            float solarFlux = (float) Math.cos(latRad);
            solarFlux = Math.max(0f, solarFlux);

            for (int lon = 0; lon < LON_STEPS; lon++) {
                int idx = lat * LON_STEPS + lon;
                float baseTemp = surfaceTemp * (0.6f + 0.4f * solarFlux);

                float altitudeKm = heightGrid[idx] * maxElevationKm;
                if (altitudeKm > 0f) {
                    baseTemp -= LAPSE_RATE * altitudeKm;
                }

                // Ocean moderates temperature toward mean
                if (heightGrid[idx] <= seaLevel) {
                    baseTemp = baseTemp * 0.7f + surfaceTemp * 0.85f * 0.3f;
                }

                temperature[idx] = baseTemp;
            }
        }

        // Step 2: wind
        WindModel.compute(windU, windV, LON_STEPS, LAT_STEPS, planet.tidallyLocked, perturbation);

        // Step 3: moisture transport and precipitation
        MoistureTransport.compute(precipitation, moisture, windU, windV,
                heightGrid, seaLevel, LON_STEPS, LAT_STEPS);

        return new ClimateData(LON_STEPS, LAT_STEPS, temperature, precipitation, windU, windV);
    }
}
