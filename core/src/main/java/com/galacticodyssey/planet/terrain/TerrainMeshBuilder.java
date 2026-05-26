package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;

public final class TerrainMeshBuilder {
    public static final int GRID_SIZE = 33;
    public static final int VERTEX_STRIDE = 10; // pos(3) + normal(3) + color(4)

    public static class MeshData {
        public final float[] vertices;
        public final short[] indices;
        public MeshData(float[] vertices, short[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }
    }

    public static MeshData build(CubeFace face, float u0, float v0, float u1, float v1,
                                  TerrainNoiseStack noise, BiomeMap biomeMap,
                                  float planetRadius, int lod, int[] neighborLods) {
        float[] vertices = new float[GRID_SIZE * GRID_SIZE * VERTEX_STRIDE];
        float[] heights = new float[GRID_SIZE * GRID_SIZE];
        Vector3[] positions = new Vector3[GRID_SIZE * GRID_SIZE];

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                float u = u0 + (u1 - u0) * gx / (GRID_SIZE - 1f);
                float v = v0 + (v1 - v0) * gy / (GRID_SIZE - 1f);
                Vector3 dir = CubeSphere.toSphere(face, u, v);
                float h = noise.heightAt(dir, biomeMap, lod);
                heights[gy * GRID_SIZE + gx] = h;
                positions[gy * GRID_SIZE + gx] = dir.scl(planetRadius + h * planetRadius * 0.01f);
            }
        }

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int idx = gy * GRID_SIZE + gx;
                Vector3 pos = positions[idx];
                Vector3 normal = computeNormal(positions, gx, gy);

                float lat = CubeSphere.latitudeOf(pos.cpy().nor());
                float lon = CubeSphere.longitudeOf(pos.cpy().nor());
                BiomeType biome = biomeMap.getBiome(lat, lon, heights[idx]);
                float[] color = biomeColor(biome);

                int vi = idx * VERTEX_STRIDE;
                vertices[vi    ] = pos.x;
                vertices[vi + 1] = pos.y;
                vertices[vi + 2] = pos.z;
                vertices[vi + 3] = normal.x;
                vertices[vi + 4] = normal.y;
                vertices[vi + 5] = normal.z;
                vertices[vi + 6] = color[0];
                vertices[vi + 7] = color[1];
                vertices[vi + 8] = color[2];
                vertices[vi + 9] = color[3];
            }
        }

        short[] indices = buildIndices(neighborLods);
        return new MeshData(vertices, indices);
    }

    private static Vector3 computeNormal(Vector3[] positions, int gx, int gy) {
        int cx = Math.max(0, Math.min(GRID_SIZE - 1, gx));
        int cy = Math.max(0, Math.min(GRID_SIZE - 1, gy));
        int left = cy * GRID_SIZE + Math.max(0, cx - 1);
        int right = cy * GRID_SIZE + Math.min(GRID_SIZE - 1, cx + 1);
        int down = Math.max(0, cy - 1) * GRID_SIZE + cx;
        int up = Math.min(GRID_SIZE - 1, cy + 1) * GRID_SIZE + cx;

        Vector3 dx = positions[right].cpy().sub(positions[left]);
        Vector3 dy = positions[up].cpy().sub(positions[down]);
        return dx.crs(dy).nor();
    }

    private static short[] buildIndices(int[] neighborLods) {
        int quads = (GRID_SIZE - 1) * (GRID_SIZE - 1);
        short[] indices = new short[quads * 6];
        int i = 0;
        for (int gy = 0; gy < GRID_SIZE - 1; gy++) {
            for (int gx = 0; gx < GRID_SIZE - 1; gx++) {
                short tl = (short)(gy * GRID_SIZE + gx);
                short tr = (short)(gy * GRID_SIZE + gx + 1);
                short bl = (short)((gy + 1) * GRID_SIZE + gx);
                short br = (short)((gy + 1) * GRID_SIZE + gx + 1);
                indices[i++] = tl;
                indices[i++] = bl;
                indices[i++] = tr;
                indices[i++] = tr;
                indices[i++] = bl;
                indices[i++] = br;
            }
        }
        return indices;
    }

    private static float[] biomeColor(BiomeType biome) {
        return switch (biome) {
            case OCEAN ->           new float[] { 0.1f, 0.3f, 0.7f, 1f };
            case LAKE ->            new float[] { 0.2f, 0.4f, 0.8f, 1f };
            case ICE_SHEET ->       new float[] { 0.9f, 0.95f, 1.0f, 1f };
            case ICE_FIELD ->       new float[] { 0.8f, 0.85f, 0.9f, 1f };
            case TUNDRA ->          new float[] { 0.6f, 0.65f, 0.55f, 1f };
            case POLAR_DESERT ->    new float[] { 0.7f, 0.7f, 0.65f, 1f };
            case DESERT ->          new float[] { 0.85f, 0.75f, 0.45f, 1f };
            case ARID_SHRUB ->      new float[] { 0.7f, 0.65f, 0.4f, 1f };
            case STEPPE ->          new float[] { 0.65f, 0.6f, 0.35f, 1f };
            case GRASSLAND ->       new float[] { 0.4f, 0.7f, 0.3f, 1f };
            case SAVANNA ->         new float[] { 0.75f, 0.7f, 0.35f, 1f };
            case SWAMP ->           new float[] { 0.3f, 0.5f, 0.25f, 1f };
            case TEMPERATE_FOREST ->new float[] { 0.2f, 0.55f, 0.2f, 1f };
            case BOREAL_FOREST ->   new float[] { 0.15f, 0.4f, 0.2f, 1f };
            case TROPICAL_FOREST -> new float[] { 0.1f, 0.5f, 0.15f, 1f };
            case ROCKY_WASTE ->     new float[] { 0.5f, 0.45f, 0.4f, 1f };
            case BADLANDS ->        new float[] { 0.7f, 0.4f, 0.25f, 1f };
            case VOLCANIC ->        new float[] { 0.3f, 0.15f, 0.1f, 1f };
        };
    }
}
