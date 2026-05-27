package com.galacticodyssey.galaxy;

public class GalaxyNoise {

    private final SimplexNoise simplex;

    public GalaxyNoise(long seed) {
        this.simplex = new SimplexNoise(seed);
    }

    public float fbm(float x, float y, int octaves, float persistence, float lacunarity) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            value += simplex.noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }

    public float fbm(float x, float y, float z, int octaves, float persistence, float lacunarity) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            value += simplex.noise(x * frequency, y * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }

    public float warpedNoise(float x, float y, float warpStrength) {
        float warpX = fbm(x + 1.7f, y + 9.2f, 4, 0.5f, 2.0f);
        float warpY = fbm(x + 8.3f, y + 2.8f, 4, 0.5f, 2.0f);
        return fbm(x + warpStrength * warpX, y + warpStrength * warpY, 6, 0.5f, 2.0f);
    }
}
