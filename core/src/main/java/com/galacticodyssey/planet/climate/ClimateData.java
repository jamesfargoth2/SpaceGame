package com.galacticodyssey.planet.climate;

public final class ClimateData {
    public final int lonSteps;
    public final int latSteps;
    public final float[] temperature;
    public final float[] precipitation;
    public final float[] windU;
    public final float[] windV;

    public ClimateData(int lonSteps, int latSteps, float[] temperature,
                       float[] precipitation, float[] windU, float[] windV) {
        this.lonSteps = lonSteps;
        this.latSteps = latSteps;
        this.temperature = temperature;
        this.precipitation = precipitation;
        this.windU = windU;
        this.windV = windV;
    }

    public float sampleTemperature(float lonRad, float latRad) {
        return sample(temperature, lonRad, latRad);
    }

    public float samplePrecipitation(float lonRad, float latRad) {
        return sample(precipitation, lonRad, latRad);
    }

    private float sample(float[] grid, float lonRad, float latRad) {
        float lonNorm = (lonRad + (float) Math.PI) / (2f * (float) Math.PI);
        float latNorm = (latRad + (float) Math.PI / 2f) / (float) Math.PI;
        lonNorm = Math.max(0f, Math.min(1f - 1e-6f, lonNorm));
        latNorm = Math.max(0f, Math.min(1f - 1e-6f, latNorm));

        float fx = lonNorm * (lonSteps - 1);
        float fy = latNorm * (latSteps - 1);
        int ix = (int) fx;
        int iy = (int) fy;
        float fracX = fx - ix;
        float fracY = fy - iy;
        int ix1 = (ix + 1) % lonSteps;
        int iy1 = Math.min(iy + 1, latSteps - 1);

        float v00 = grid[iy * lonSteps + ix];
        float v10 = grid[iy * lonSteps + ix1];
        float v01 = grid[iy1 * lonSteps + ix];
        float v11 = grid[iy1 * lonSteps + ix1];

        float top = v00 + (v10 - v00) * fracX;
        float bot = v01 + (v11 - v01) * fracX;
        return top + (bot - top) * fracY;
    }
}
