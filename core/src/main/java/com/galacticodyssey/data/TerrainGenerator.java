// core/src/main/java/com/galacticodyssey/data/TerrainGenerator.java
package com.galacticodyssey.data;

import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.CraterCarver;
import com.galacticodyssey.planet.CraterSpec;
import com.galacticodyssey.planet.terrain.DrainageNetwork;
import com.galacticodyssey.planet.terrain.HydraulicErosion;
import com.galacticodyssey.planet.terrain.ThermalErosion;
import java.util.List;
import java.util.Random;

public final class TerrainGenerator {

    private TerrainGenerator() {}

    public static float[] generateHeightmap(int vertsX, int vertsZ, float worldWidth, float worldDepth, long seed) {
        float[] heights = new float[vertsX * vertsZ];
        // Independent noise instances per layer prevent cross-frequency correlation
        GalaxyNoise continentNoise = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.CONTINENT_NOISE_DOMAIN));
        GalaxyNoise mountainNoise  = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.RIDGE_NOISE_DOMAIN));
        GalaxyNoise hillNoise      = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.DETAIL_NOISE_DOMAIN));
        GalaxyNoise detailNoise    = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.BIOME_DOMAIN));

        float cellWidth = worldWidth / (vertsX - 1);
        float cellDepth = worldDepth / (vertsZ - 1);
        float halfWidth = worldWidth / 2f;
        float halfDepth = worldDepth / 2f;

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                float wx = x * cellWidth - halfWidth;
                float wz = z * cellDepth - halfDepth;

                float continent = continentNoise.domainWarp2D(wx * 0.002f, wz * 0.002f, 0.7f, 3, 6) * 40f;
                float mountains = mountainNoise.ridgedFbm(wx * 0.004f, wz * 0.004f, 8, 2.0f, 2.0f) * 60f;
                float hills     = hillNoise.fbm(wx * 0.01f, wz * 0.01f, 6, 0.5f, 2.0f) * 15f;
                float detail    = detailNoise.billowFbm(wx * 0.04f, wz * 0.04f, 4, 0.5f, 2.0f) * 3f;

                float h = continent + mountains + hills + detail;
                heights[z * vertsX + x] = h;
            }
        }

        long erosionSeed = SeedDeriver.domain(seed, SeedDeriver.EROSION_DOMAIN);
        HydraulicErosion.erode(heights, vertsX, vertsZ, erosionSeed, 70000);
        ThermalErosion.erode(heights, vertsX, vertsZ, 0.6f, 50);

        DrainageNetwork.Result drainage = DrainageNetwork.compute(
            heights, vertsX, vertsZ, vertsX * 0.5f);
        for (int i = 0; i < heights.length; i++) {
            if (drainage.isRiver[i]) {
                float depth = Math.min(5f, drainage.flowAccumulation[i] * 0.01f);
                heights[i] -= depth;
            }
        }

        flattenSpawnArea(heights, vertsX, vertsZ, worldWidth, worldDepth);

        return heights;
    }

    /**
     * Flattens a circular area around the world origin so the player spawns on
     * level ground. Inside {@code flatRadius} the height is constant; between
     * {@code flatRadius} and {@code blendRadius} it smoothly interpolates back
     * to the natural terrain via a cubic ease curve.
     */
    static void flattenSpawnArea(float[] heights, int vertsX, int vertsZ,
                                  float worldWidth, float worldDepth) {
        float flatRadius = 40f;
        float blendRadius = 80f;

        float cellW = worldWidth / (vertsX - 1);
        float cellD = worldDepth / (vertsZ - 1);
        float halfW = worldWidth / 2f;
        float halfD = worldDepth / 2f;

        int centerX = vertsX / 2;
        int centerZ = vertsZ / 2;
        float flatHeight = heights[centerZ * vertsX + centerX];

        int margin = (int) Math.ceil(blendRadius / Math.min(cellW, cellD));
        int x0 = Math.max(0, centerX - margin);
        int x1 = Math.min(vertsX - 1, centerX + margin);
        int z0 = Math.max(0, centerZ - margin);
        int z1 = Math.min(vertsZ - 1, centerZ + margin);

        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                float wx = x * cellW - halfW;
                float wz = z * cellD - halfD;
                float dist = (float) Math.sqrt(wx * wx + wz * wz);

                if (dist < flatRadius) {
                    heights[z * vertsX + x] = flatHeight;
                } else if (dist < blendRadius) {
                    float t = (dist - flatRadius) / (blendRadius - flatRadius);
                    t = t * t * (3f - 2f * t);
                    heights[z * vertsX + x] = flatHeight + (heights[z * vertsX + x] - flatHeight) * t;
                }
            }
        }
    }

    public static float getHeightAt(float[] heights, int vertsX, int vertsZ,
                                     float worldWidth, float worldDepth,
                                     float worldX, float worldZ) {
        float halfW = worldWidth / 2f;
        float halfD = worldDepth / 2f;
        float fx = (worldX + halfW) / worldWidth * (vertsX - 1);
        float fz = (worldZ + halfD) / worldDepth * (vertsZ - 1);

        int ix = Math.max(0, Math.min(vertsX - 2, (int) fx));
        int iz = Math.max(0, Math.min(vertsZ - 2, (int) fz));
        float fracX = fx - ix;
        float fracZ = fz - iz;

        float h00 = heights[iz * vertsX + ix];
        float h10 = heights[iz * vertsX + ix + 1];
        float h01 = heights[(iz + 1) * vertsX + ix];
        float h11 = heights[(iz + 1) * vertsX + ix + 1];

        float h0 = h00 + (h10 - h00) * fracX;
        float h1 = h01 + (h11 - h01) * fracX;
        return h0 + (h1 - h0) * fracZ;
    }

    public static float[] computeNormals(float[] heights, int vertsX, int vertsZ,
                                          float worldWidth, float worldDepth) {
        float[] normals = new float[vertsX * vertsZ * 3];
        float cellW = worldWidth / (vertsX - 1);
        float cellD = worldDepth / (vertsZ - 1);

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                float hL = heights[z * vertsX + Math.max(0, x - 1)];
                float hR = heights[z * vertsX + Math.min(vertsX - 1, x + 1)];
                float hD = heights[Math.max(0, z - 1) * vertsX + x];
                float hU = heights[Math.min(vertsZ - 1, z + 1) * vertsX + x];

                float nx = (hL - hR) / (2f * cellW);
                float nz = (hD - hU) / (2f * cellD);
                float ny = 1f;

                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int idx = (z * vertsX + x) * 3;
                normals[idx] = nx / len;
                normals[idx + 1] = ny / len;
                normals[idx + 2] = nz / len;
            }
        }
        return normals;
    }

    public static void applyBiomeVariation(float[] heights, int vertsX, int vertsZ,
                                           float worldWidth, float worldDepth,
                                           BiomeType biome, long seed) {
        GalaxyNoise smoothNoise = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.DETAIL_NOISE_DOMAIN));
        GalaxyNoise ridgedNoise = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.RIDGE_NOISE_DOMAIN));

        float cellWidth = worldWidth / (vertsX - 1);
        float cellDepth = worldDepth / (vertsZ - 1);
        float halfWidth = worldWidth / 2f;
        float halfDepth = worldDepth / 2f;

        float amp = biome.amplitude;
        float ridge = biome.ridgeMix;

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                float wx = x * cellWidth - halfWidth;
                float wz = z * cellDepth - halfDepth;

                float smooth = smoothNoise.fbm(wx * 0.01f, wz * 0.01f, 5, 0.5f, 2.0f);
                float ridged = ridgedNoise.ridgedFbm(wx * 0.015f, wz * 0.015f, 6, 2.0f, 2.0f);
                float blended = smooth * (1.0f - ridge) + ridged * ridge;
                heights[z * vertsX + x] += blended * amp * 50f;
            }
        }
    }

    /**
     * Stamps a list of craters onto the heightmap at seeded positions.
     * Each crater is placed deterministically based on the seed.
     */
    public static void stampCraters(float[] heights, int vertsX, int vertsZ,
                                    float worldWidth, float worldDepth,
                                    List<CraterSpec> craters, long seed) {
        Random rng = new Random(seed);

        // heightScale: convert km to heightmap units.
        // Assume worldWidth represents ~1000 km of surface by default;
        // scale crater depth relative to the heightmap's coordinate range.
        float kmPerVertex = worldWidth / (vertsX - 1);
        float heightScale = 1.0f; // 1 km = 1 heightmap unit

        for (CraterSpec crater : craters) {
            // Place crater at a seeded random position within the heightmap
            int cx = rng.nextInt(vertsX);
            int cz = rng.nextInt(vertsZ);

            // Rim radius in vertex units
            float rimRadiusKm = crater.diameterKm * 0.5f;
            float rimR = rimRadiusKm / kmPerVertex;

            // Skip craters too small to represent on this grid
            if (rimR < 0.5f) continue;

            CraterCarver.carve(heights, vertsX, vertsZ, cx, cz, rimR, crater, heightScale);
        }
    }

    private static int[] createPermutation(Random rng) {
        int[] p = new int[512];
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = base[i]; base[i] = base[j]; base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) p[i] = base[i & 255];
        return p;
    }

    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;

    private static final int[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static float noise2D(int[] perm, float x, float y) {
        float s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;

        int ii = i & 255;
        int jj = j & 255;

        float n0 = cornerContribution(perm, x0, y0, ii, jj);
        float n1 = cornerContribution(perm, x1, y1, ii + i1, jj + j1);
        float n2 = cornerContribution(perm, x2, y2, ii + 1, jj + 1);

        return 70f * (n0 + n1 + n2);
    }

    private static float cornerContribution(int[] perm, float x, float y, int gi, int gj) {
        float t = 0.5f - x * x - y * y;
        if (t < 0) return 0;
        t *= t;
        int[] g = GRAD2[perm[perm[gi & 255] + (gj & 255)] & 7];
        return t * t * (g[0] * x + g[1] * y);
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
