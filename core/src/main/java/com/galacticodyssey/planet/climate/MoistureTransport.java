package com.galacticodyssey.planet.climate;

import com.galacticodyssey.planet.HeightSampler;

/**
 * Advects moisture along wind vectors from ocean sources.
 * Implements orographic lift: when wind encounters mountains,
 * moisture precipitates on the windward side (rain shadow effect).
 */
public final class MoistureTransport {

    private MoistureTransport() {}

    private static final int ADVECTION_STEPS = 40;
    private static final float EVAP_RATE = 0.02f;
    private static final float OROGRAPHIC_FACTOR = 3.0f;

    public static void compute(float[] precipitation, float[] moisture,
                               float[] windU, float[] windV,
                               float[] heightGrid, float seaLevel,
                               int lonSteps, int latSteps) {
        float[] currentMoisture = new float[lonSteps * latSteps];
        for (int i = 0; i < currentMoisture.length; i++) {
            currentMoisture[i] = heightGrid[i] <= seaLevel ? 1.0f : 0.0f;
        }

        float[] totalPrecip = new float[lonSteps * latSteps];

        for (int step = 0; step < ADVECTION_STEPS; step++) {
            float[] nextMoisture = new float[lonSteps * latSteps];

            for (int lat = 0; lat < latSteps; lat++) {
                for (int lon = 0; lon < lonSteps; lon++) {
                    int idx = lat * lonSteps + lon;

                    float u = windU[idx];
                    float v = windV[idx];

                    // Trace backward to find source cell
                    float srcLon = lon - u * 1.5f;
                    float srcLat = lat - v * 1.5f;

                    // Wrap longitude, clamp latitude
                    srcLon = ((srcLon % lonSteps) + lonSteps) % lonSteps;
                    srcLat = Math.max(0f, Math.min(latSteps - 1.001f, srcLat));

                    float transported = bilinearSample(currentMoisture, lonSteps, latSteps, srcLon, srcLat);

                    // Orographic lift: height difference between here and upwind
                    float hereH = heightGrid[idx];
                    int srcLonI = ((int) srcLon) % lonSteps;
                    int srcLatI = Math.min((int) srcLat, latSteps - 1);
                    float upwindH = heightGrid[srcLatI * lonSteps + srcLonI];
                    float lift = Math.max(0f, hereH - upwindH);
                    float precip = transported * Math.min(1f, lift * OROGRAPHIC_FACTOR);

                    // Base precipitation from condensation
                    precip += transported * 0.02f;

                    totalPrecip[idx] += precip;
                    float remaining = (transported - precip) * (1f - EVAP_RATE);

                    // Re-saturate ocean cells
                    if (heightGrid[idx] <= seaLevel) {
                        remaining = 1.0f;
                    }

                    nextMoisture[idx] = Math.max(0f, Math.min(1f, remaining));
                }
            }

            System.arraycopy(nextMoisture, 0, currentMoisture, 0, currentMoisture.length);
        }

        // Normalize precipitation to [0, 1]
        float maxPrecip = 0f;
        for (float p : totalPrecip) maxPrecip = Math.max(maxPrecip, p);
        if (maxPrecip > 0f) {
            for (int i = 0; i < totalPrecip.length; i++) {
                precipitation[i] = totalPrecip[i] / maxPrecip;
            }
        }

        System.arraycopy(currentMoisture, 0, moisture, 0, moisture.length);
    }

    private static float bilinearSample(float[] grid, int w, int h, float x, float y) {
        int x0 = ((int) x) % w;
        int x1 = (x0 + 1) % w;
        int y0 = Math.min((int) y, h - 1);
        int y1 = Math.min(y0 + 1, h - 1);
        float fx = x - (int) x;
        float fy = y - (int) y;

        float v00 = grid[y0 * w + x0];
        float v10 = grid[y0 * w + x1];
        float v01 = grid[y1 * w + x0];
        float v11 = grid[y1 * w + x1];

        float top = v00 + (v10 - v00) * fx;
        float bot = v01 + (v11 - v01) * fx;
        return top + (bot - top) * fy;
    }
}
