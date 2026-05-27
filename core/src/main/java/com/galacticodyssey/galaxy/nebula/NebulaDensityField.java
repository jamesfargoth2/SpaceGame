package com.galacticodyssey.galaxy.nebula;

import com.galacticodyssey.galaxy.SimplexNoise;

/**
 * Evaluates gas density at a point within a nebula volume using domain-warped fBm noise.
 */
public final class NebulaDensityField {

    /**
     * Samples the density at a normalised position within a nebula volume.
     *
     * @param nx normalised x position (0 = centre, 1 = rim)
     * @param ny normalised y position
     * @param nz normalised z position
     * @param vol the nebula volume data
     * @param noiseSeed seed for noise generation
     * @return density value clamped between 0 and vol.peakDensity
     */
    public float density(float nx, float ny, float nz, NebulaVolume vol, long noiseSeed) {
        float r = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        // Radial falloff
        float falloff = (float) Math.exp(-r * r * 2f);

        // Domain-warped fBm for filament structure
        SimplexNoise warpNoise = new SimplexNoise(noiseSeed);
        SimplexNoise mainNoise = new SimplexNoise(noiseSeed + 1);
        SimplexNoise clumpNoise = new SimplexNoise(noiseSeed + 2);

        // Domain warping using 2D noise with different coordinate pairs for 3D effect
        float warpX = fbm2d(warpNoise, nx + 1.7f, ny + 9.2f, 3, 0.5f, 2.0f);
        float warpY = fbm2d(warpNoise, ny + 8.3f, nz + 2.8f, 3, 0.5f, 2.0f);

        // Main filament structure
        float filament = fbm2d(mainNoise,
                nx + warpX * 0.3f, ny + warpY * 0.3f,
                5, 0.5f, 2.0f);
        filament = (filament + 1f) * 0.5f; // Normalise to 0..1

        // Small-scale clumping
        float clump = fbm2d(clumpNoise, nx * 4f, ny * 4f, 3, 0.6f, 2.5f);
        clump = (clump + 1f) * 0.5f;

        float density = falloff * (filament * 0.7f + clump * 0.3f) * vol.peakDensity;
        return Math.max(0f, Math.min(vol.peakDensity, density));
    }

    private float fbm2d(SimplexNoise noise, float x, float y,
                         int octaves, float persistence, float lacunarity) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            value += noise.noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }
}
