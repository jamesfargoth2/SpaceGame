package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.geometry.ProceduralMeshData;

public final class PatternStamper {

    private PatternStamper() {}

    public static float[] stamp(ProceduralMeshData data, PartGeometrySpec spec, long colorSeed) {
        int vertCount = data.positionCount();
        float[] colors = new float[vertCount * 4];

        float length = Math.max(0.001f, spec.length);
        float maxRadius = Math.max(0.001f, spec.radius);

        for (int v = 0; v < vertCount; v++) {
            float px = data.positions[v * 3];
            float py = data.positions[v * 3 + 1];
            float pz = data.positions[v * 3 + 2];
            float ny = data.normals[v * 3 + 1];

            float dorsalVentral = 0.5f - ny * 0.5f;
            float limbAxis = Math.max(0f, Math.min(1f, pz / length));
            float hash = spatialHash(px, py, pz, colorSeed);
            float radialDist = (float) Math.sqrt(px * px + py * py);
            float curvature = 1f - Math.min(1f, radialDist / maxRadius);

            colors[v * 4]     = clamp01(dorsalVentral);
            colors[v * 4 + 1] = clamp01(limbAxis);
            colors[v * 4 + 2] = clamp01(hash);
            colors[v * 4 + 3] = clamp01(curvature);
        }

        return colors;
    }

    private static float spatialHash(float x, float y, float z, long seed) {
        long h = seed;
        h ^= Float.floatToIntBits(x * 7.13f) * 0x9E3779B97F4A7C15L;
        h ^= Float.floatToIntBits(y * 11.37f) * 0x6C62272E07BB0142L;
        h ^= Float.floatToIntBits(z * 5.91f) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (float) ((h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
