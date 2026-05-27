package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

import java.util.Random;

public final class NebulaVolumetricGenerator {

    public NebulaVolume generate(long seed, NebulaType type, float radius, int resolution) {
        long nebSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.NEBULA_DOMAIN), 0);
        Random rng = new Random(nebSeed);
        SimplexNoise noise = new SimplexNoise(nebSeed);

        float[] densityField = new float[resolution * resolution * resolution];
        float[] colorField   = new float[resolution * resolution * resolution * 3];

        Color dominantColor   = getDominantColor(type, rng);
        Color secondaryColor  = getSecondaryColor(type, rng);

        float offsetX = rng.nextFloat() * 100f;
        float offsetY = rng.nextFloat() * 100f;
        float offsetZ = rng.nextFloat() * 100f;

        float cellSize = (radius * 2f) / resolution;

        for (int z = 0; z < resolution; z++) {
            for (int y = 0; y < resolution; y++) {
                for (int x = 0; x < resolution; x++) {
                    float wx = (x - resolution / 2f) * cellSize;
                    float wy = (y - resolution / 2f) * cellSize;
                    float wz = (z - resolution / 2f) * cellSize;

                    float distFromCenter = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                    float radialFalloff  = Math.max(0f, 1f - distFromCenter / radius);

                    // Domain-warped fBm over 5 octaves
                    float density  = 0f;
                    float amplitude = 1f;
                    float frequency = 1f / radius;
                    for (int octave = 0; octave < 5; octave++) {
                        float nx = (wx + offsetX) * frequency;
                        float ny = (wy + offsetY) * frequency;
                        float nz = (wz + offsetZ) * frequency;
                        density   += noise.noise(nx, ny, nz) * amplitude;
                        amplitude *= 0.5f;
                        frequency *= 2.0f;
                    }

                    density = (density + 1f) * 0.5f;                        // remap to [0,1]
                    density *= radialFalloff * radialFalloff;
                    density  = applyTypeModification(type, density, distFromCenter, radius);

                    int idx = z * resolution * resolution + y * resolution + x;
                    densityField[idx] = Math.max(0f, Math.min(1f, density));

                    float colorT = density * 0.7f + (distFromCenter / radius) * 0.3f;
                    colorT = Math.max(0f, Math.min(1f, colorT));
                    int cIdx = idx * 3;
                    colorField[cIdx]     = MathUtils.lerp(dominantColor.r, secondaryColor.r, colorT);
                    colorField[cIdx + 1] = MathUtils.lerp(dominantColor.g, secondaryColor.g, colorT);
                    colorField[cIdx + 2] = MathUtils.lerp(dominantColor.b, secondaryColor.b, colorT);
                }
            }
        }

        return new NebulaVolume(nebSeed, densityField, colorField, resolution, radius, type, dominantColor);
    }

    private float applyTypeModification(NebulaType type, float density, float dist, float radius) {
        return switch (type) {
            case EMISSION -> {
                float coreBoost = Math.max(0f, 1f - dist / (radius * 0.3f));
                yield density + coreBoost * 0.4f;
            }
            case DARK -> {
                yield density > 0.4f ? density * 1.5f : density * 0.3f;
            }
            case REFLECTION -> {
                float edgeBright = Math.max(0f, (dist / radius) - 0.6f) * 0.5f;
                yield density + edgeBright;
            }
            case PLANETARY -> {
                float shellDist = Math.abs(dist - radius * 0.5f) / (radius * 0.2f);
                float shell     = Math.max(0f, 1f - shellDist);
                yield density * 0.5f + shell * 0.5f;
            }
        };
    }

    private Color getDominantColor(NebulaType type, Random rng) {
        return switch (type) {
            case EMISSION -> new Color(
                RngUtil.range(rng, 0.8f, 1.0f),
                RngUtil.range(rng, 0.1f, 0.4f),
                RngUtil.range(rng, 0.2f, 0.5f), 1f);
            case REFLECTION -> new Color(
                RngUtil.range(rng, 0.3f, 0.6f),
                RngUtil.range(rng, 0.4f, 0.7f),
                RngUtil.range(rng, 0.7f, 1.0f), 1f);
            case DARK -> new Color(
                RngUtil.range(rng, 0.05f, 0.15f),
                RngUtil.range(rng, 0.02f, 0.10f),
                RngUtil.range(rng, 0.05f, 0.15f), 1f);
            case PLANETARY -> new Color(
                RngUtil.range(rng, 0.2f, 0.5f),
                RngUtil.range(rng, 0.6f, 0.9f),
                RngUtil.range(rng, 0.3f, 0.6f), 1f);
        };
    }

    private Color getSecondaryColor(NebulaType type, Random rng) {
        return switch (type) {
            case EMISSION -> new Color(
                RngUtil.range(rng, 0.9f, 1.0f),
                RngUtil.range(rng, 0.6f, 0.9f),
                RngUtil.range(rng, 0.1f, 0.3f), 1f);
            case REFLECTION -> new Color(
                RngUtil.range(rng, 0.6f, 0.9f),
                RngUtil.range(rng, 0.7f, 1.0f),
                RngUtil.range(rng, 0.9f, 1.0f), 1f);
            case DARK -> new Color(
                RngUtil.range(rng, 0.10f, 0.20f),
                RngUtil.range(rng, 0.05f, 0.15f),
                RngUtil.range(rng, 0.10f, 0.25f), 1f);
            case PLANETARY -> new Color(
                RngUtil.range(rng, 0.1f, 0.3f),
                RngUtil.range(rng, 0.8f, 1.0f),
                RngUtil.range(rng, 0.6f, 0.9f), 1f);
        };
    }
}
