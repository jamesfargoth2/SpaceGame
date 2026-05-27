package com.galacticodyssey.galaxy;

import java.util.Random;

public final class SimplexNoise {

    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;
    private static final float F3 = 1.0f / 3.0f;
    private static final float G3 = 1.0f / 6.0f;

    private static final int[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final int[][] GRAD3 = {
        {1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
        {1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
        {0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}
    };

    private final int[] perm = new int[512];

    public SimplexNoise(long seed) {
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = base[i];
            base[i] = base[j];
            base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) perm[i] = base[i & 255];
    }

    public float noise(float x, float y) {
        float s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);

        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else          { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;

        int ii = i & 255;
        int jj = j & 255;

        float n0 = contribution(x0, y0, ii, jj);
        float n1 = contribution(x1, y1, ii + i1, jj + j1);
        float n2 = contribution(x2, y2, ii + 1, jj + 1);

        return 70f * (n0 + n1 + n2);
    }

    public float noise(float x, float y, float z) {
        float s = (x + y + z) * F3;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        int k = fastFloor(z + s);

        float t = (i + j + k) * G3;
        float x0 = x - (i - t);
        float y0 = y - (j - t);
        float z0 = z - (k - t);

        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0)      { i1=1; j1=0; k1=0; i2=1; j2=1; k2=0; }
            else if (x0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=0; k2=1; }
            else               { i1=0; j1=0; k1=1; i2=1; j2=0; k2=1; }
        } else {
            if (y0 < z0)       { i1=0; j1=0; k1=1; i2=0; j2=1; k2=1; }
            else if (x0 < z0)  { i1=0; j1=1; k1=0; i2=0; j2=1; k2=1; }
            else               { i1=0; j1=1; k1=0; i2=1; j2=1; k2=0; }
        }

        float x1 = x0 - i1 + G3;
        float y1 = y0 - j1 + G3;
        float z1 = z0 - k1 + G3;
        float x2 = x0 - i2 + 2f * G3;
        float y2 = y0 - j2 + 2f * G3;
        float z2 = z0 - k2 + 2f * G3;
        float x3 = x0 - 1f + 3f * G3;
        float y3 = y0 - 1f + 3f * G3;
        float z3 = z0 - 1f + 3f * G3;

        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;

        float n0 = contribution3(x0, y0, z0, ii, jj, kk);
        float n1 = contribution3(x1, y1, z1, ii + i1, jj + j1, kk + k1);
        float n2 = contribution3(x2, y2, z2, ii + i2, jj + j2, kk + k2);
        float n3 = contribution3(x3, y3, z3, ii + 1, jj + 1, kk + 1);

        return 32f * (n0 + n1 + n2 + n3);
    }

    private float contribution3(float x, float y, float z, int gi, int gj, int gk) {
        float t = 0.6f - x * x - y * y - z * z;
        if (t < 0f) return 0f;
        t *= t;
        int[] g = GRAD3[perm[(gi + perm[(gj + perm[gk & 255]) & 255]) & 511] % 12];
        return t * t * (g[0] * x + g[1] * y + g[2] * z);
    }

    private float contribution(float x, float y, int gi, int gj) {
        float t = 0.5f - x * x - y * y;
        if (t < 0f) return 0f;
        t *= t;
        int[] g = GRAD2[perm[(gi + perm[gj & 255]) & 511] & 7];
        return t * t * (g[0] * x + g[1] * y);
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
