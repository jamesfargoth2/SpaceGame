package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;

public final class NebulaVolume {
    public final long seed;
    public final float[] densityField;
    public final float[] colorField;
    public final int resolution;
    public final float boundingRadius;
    public final NebulaType type;
    public final Color dominantColor;

    public NebulaVolume(long seed, float[] densityField, float[] colorField, int resolution,
                        float boundingRadius, NebulaType type, Color dominantColor) {
        this.seed = seed;
        this.densityField = densityField;
        this.colorField = colorField;
        this.resolution = resolution;
        this.boundingRadius = boundingRadius;
        this.type = type;
        this.dominantColor = new Color(dominantColor);
    }

    public float getDensity(int x, int y, int z) {
        return densityField[z * resolution * resolution + y * resolution + x];
    }

    public void getColor(int x, int y, int z, Color out) {
        int idx = (z * resolution * resolution + y * resolution + x) * 3;
        out.r = colorField[idx];
        out.g = colorField[idx + 1];
        out.b = colorField[idx + 2];
        out.a = 1f;
    }
}
