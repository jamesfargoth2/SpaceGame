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

    public float ridgedFbm(float x, float y, int octaves, float lacunarity, float gain) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float weight = 1f;
        for (int i = 0; i < octaves; i++) {
            float signal = simplex.noise(x * frequency, y * frequency);
            signal = 1.0f - Math.abs(signal);
            signal *= signal;
            signal *= weight;
            weight = Math.max(0f, Math.min(1f, signal * gain));
            value += signal * amplitude;
            amplitude *= 0.5f;
            frequency *= lacunarity;
        }
        return value;
    }

    public float ridgedFbm(float x, float y, float z, int octaves, float lacunarity, float gain) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float weight = 1f;
        for (int i = 0; i < octaves; i++) {
            float signal = simplex.noise(x * frequency, y * frequency, z * frequency);
            signal = 1.0f - Math.abs(signal);
            signal *= signal;
            signal *= weight;
            weight = Math.max(0f, Math.min(1f, signal * gain));
            value += signal * amplitude;
            amplitude *= 0.5f;
            frequency *= lacunarity;
        }
        return value;
    }

    public float billowFbm(float x, float y, int octaves, float persistence, float lacunarity) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            float signal = Math.abs(simplex.noise(x * frequency, y * frequency)) * 2f - 1f;
            value += signal * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }

    public float billowFbm(float x, float y, float z, int octaves, float persistence, float lacunarity) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            float signal = Math.abs(simplex.noise(x * frequency, y * frequency, z * frequency)) * 2f - 1f;
            value += signal * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }

    public float domainWarp2D(float x, float y, float warpStrength,
                              int warpOctaves, int mainOctaves) {
        float wx = fbm(x + 1.7f, y + 9.2f, warpOctaves, 0.5f, 2.0f);
        float wy = fbm(x + 8.3f, y + 2.8f, warpOctaves, 0.5f, 2.0f);
        return fbm(x + warpStrength * wx, y + warpStrength * wy, mainOctaves, 0.5f, 2.0f);
    }

    public float domainWarp3D(float x, float y, float z, float warpStrength,
                              int warpOctaves, int mainOctaves) {
        float wx = fbm(x + 1.7f, y + 9.2f, z + 3.1f, warpOctaves, 0.5f, 2.0f);
        float wy = fbm(x + 8.3f, y + 2.8f, z + 7.4f, warpOctaves, 0.5f, 2.0f);
        float wz = fbm(x + 4.1f, y + 6.5f, z + 5.9f, warpOctaves, 0.5f, 2.0f);
        return fbm(x + warpStrength * wx, y + warpStrength * wy,
                   z + warpStrength * wz, mainOctaves, 0.5f, 2.0f);
    }

    public float warpedNoise(float x, float y, float warpStrength) {
        return domainWarp2D(x, y, warpStrength, 4, 6);
    }
}
