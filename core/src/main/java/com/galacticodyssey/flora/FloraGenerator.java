package com.galacticodyssey.flora;

import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.flora.data.FloraRegistry;
import com.galacticodyssey.flora.data.FloraSpecies;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Orchestrates flora: deterministic placement planning + (Task 10) prototype/instance building. */
public final class FloraGenerator {
    private FloraGenerator() {}

    /**
     * Pure, GL-free placement decision: where flora goes, what species/variant, transform.
     * Mirrors the old WorldPopulator.placeTrees scatter, but data-driven and seeded via FLORA_DOMAIN.
     */
    public static List<FloraPlacement> planPlacements(
            FloraRegistry registry, BiomeType[] biomeGrid, float[] heightmap,
            int vertsX, int vertsZ, float worldWidth, float worldDepth,
            float seaLevel, long planetSeed, int attempts) {

        List<FloraPlacement> out = new ArrayList<>();
        long floraSeed = SeedDeriver.floraDomain(planetSeed);
        Random rng = new Random(floraSeed);
        float halfW = worldWidth / 2f, halfD = worldDepth / 2f;

        for (int i = 0; i < attempts; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.9f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.9f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel + 0.5f) continue;

            int gx = clamp((int) ((wx + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
            int gz = clamp((int) ((wz + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
            BiomeType biome = biomeGrid[gz * vertsX + gx];

            BiomePalette palette = registry.palette(biome);
            if (palette == null || palette.isEmpty()) continue;
            if (rng.nextFloat() > palette.density) continue;

            String speciesId = palette.pickSpecies(rng);
            if (speciesId == null) continue;
            FloraSpecies sp = registry.species(speciesId);
            if (sp == null) continue;

            int variant = rng.nextInt(Math.max(1, sp.prototypeVariants));
            float yaw = rng.nextFloat() * 360f;
            float scale = 0.85f + rng.nextFloat() * 0.4f;
            out.add(new FloraPlacement(speciesId, variant, wx, h, wz, yaw, scale));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // buildPrototypes() + populate() added in Task 10
}
