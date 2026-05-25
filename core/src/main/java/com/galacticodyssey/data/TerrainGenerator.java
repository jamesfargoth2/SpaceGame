// core/src/main/java/com/galacticodyssey/data/TerrainGenerator.java
package com.galacticodyssey.data;

import java.util.Random;

public final class TerrainGenerator {

    private TerrainGenerator() {}

    public static float[] generateHeightmap(int vertsX, int vertsZ, float worldWidth, float worldDepth, long seed) {
        float[] heights = new float[vertsX * vertsZ];
        Random rng = new Random(seed);
        int[] perm = createPermutation(rng);

        float cellWidth = worldWidth / (vertsX - 1);
        float cellDepth = worldDepth / (vertsZ - 1);
        float halfWidth = worldWidth / 2f;
        float halfDepth = worldDepth / 2f;

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                float wx = x * cellWidth - halfWidth;
                float wz = z * cellDepth - halfDepth;
                float h = noise2D(perm, wx * 0.005f, wz * 0.005f) * 30f
                        + noise2D(perm, wx * 0.02f + 100f, wz * 0.02f + 100f) * 5f;
                heights[z * vertsX + x] = h;
            }
        }
        return heights;
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
