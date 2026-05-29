package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.Random;

/** GL-free per-cell grass instance generation. Deterministic from (cellX, cellZ, grassSeed). */
public final class GrassCell {
    public static final int STRIDE = 10;

    private GrassCell() {}

    /** Returns packed instance data (length = tuftCount * STRIDE). May be empty. */
    public static float[] generate(int cellX, int cellZ, GrassConfig config,
                                   TerrainSampler sampler, long grassSeed) {
        Random rng = new Random(SeedDeriver.forChunk(grassSeed, cellX, cellZ));
        float cell = config.cellSize;
        float originX = cellX * cell, originZ = cellZ * cell;
        int candidates = Math.round(cell * cell * config.baseTuftsPerM2);

        FloatArray out = new FloatArray(candidates * STRIDE);
        for (int i = 0; i < candidates; i++) {
            float wx = originX + rng.nextFloat() * cell;
            float wz = originZ + rng.nextFloat() * cell;
            BiomeType biome = sampler.biomeAt(wx, wz);
            GrassConfig.BiomeGrass g = config.forBiome(biome);
            if (g == null) continue;
            if (rng.nextFloat() >= g.density) continue;

            float scaleY = g.heightMin + rng.nextFloat() * (g.heightMax - g.heightMin);
            float scaleXZ = 0.8f + rng.nextFloat() * 0.4f;
            float rotationY = rng.nextFloat() * (float) (Math.PI * 2.0);
            float phase = rng.nextFloat() * (float) (Math.PI * 2.0);
            float t = rng.nextFloat();
            float r = lerp(g.colorAr, g.colorBr, t);
            float gg = lerp(g.colorAg, g.colorBg, t);
            float b = lerp(g.colorAb, g.colorBb, t);
            float oy = sampler.heightAt(wx, wz);

            out.add(wx); out.add(oy); out.add(wz);
            out.add(scaleXZ); out.add(scaleY); out.add(rotationY); out.add(phase);
            out.add(r); out.add(gg); out.add(b);
        }
        return out.toArray();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
