package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.Random;

public final class CraterGenerator {

    public CraterProfile generate(long seed, float baseRadius, float terrainScale) {
        long craterSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.CRATER_DOMAIN), 0);
        Random rng = new Random(craterSeed);

        float radius = baseRadius * RngUtil.range(rng, 0.7f, 1.3f);
        float depth = radius * RngUtil.range(rng, 0.1f, 0.3f);
        float rimHeight = depth * RngUtil.range(rng, 0.2f, 0.5f);
        float age = rng.nextFloat();

        float centralPeakHeight = 0f;
        if (radius > terrainScale * 0.1f) {
            centralPeakHeight = depth * RngUtil.range(rng, 0.1f, 0.4f);
        }

        float ejectaRadius = radius * RngUtil.range(rng, 1.5f, 2.5f);

        depth *= (1f - age * 0.6f);
        rimHeight *= (1f - age * 0.7f);
        centralPeakHeight *= (1f - age * 0.5f);

        float cx = RngUtil.range(rng, -terrainScale * 0.4f, terrainScale * 0.4f);
        float cz = RngUtil.range(rng, -terrainScale * 0.4f, terrainScale * 0.4f);

        return new CraterProfile(craterSeed, cx, cz, radius, depth, rimHeight,
            centralPeakHeight, ejectaRadius, age);
    }

    public void stampOnHeightmap(CraterProfile crater, float[] heightmap, int resolution, float mapScale) {
        float cellSize = mapScale / resolution;

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                float worldX = (x - resolution / 2f) * cellSize;
                float worldZ = (z - resolution / 2f) * cellSize;

                float dx = worldX - crater.centerX;
                float dz = worldZ - crater.centerZ;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);

                float influence = getRadialProfile(dist, crater);
                if (influence != 0f) {
                    heightmap[z * resolution + x] += influence;
                }
            }
        }
    }

    private float getRadialProfile(float dist, CraterProfile crater) {
        float r = crater.radius;

        if (dist > crater.ejectaRadius) return 0f;

        if (dist < r * 0.2f && crater.centralPeakHeight > 0f) {
            float t = dist / (r * 0.2f);
            return crater.centralPeakHeight * (float) Math.exp(-t * t * 2.0);
        }

        if (dist < r) {
            float t = dist / r;
            return -crater.depth * MathUtils.cos(t * MathUtils.PI * 0.5f);
        }

        if (dist < r * 1.2f) {
            float t = (dist - r) / (r * 0.2f);
            return crater.rimHeight * (1f - t) * (1f - t);
        }

        float t = (dist - r * 1.2f) / (crater.ejectaRadius - r * 1.2f);
        return crater.rimHeight * 0.3f * (float) Math.exp(-t * 3.0);
    }
}
