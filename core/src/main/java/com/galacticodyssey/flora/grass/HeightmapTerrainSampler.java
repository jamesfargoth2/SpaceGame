package com.galacticodyssey.flora.grass;

import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.planet.BiomeType;

/** TerrainSampler backed by the current fixed heightmap + biome grid arrays. */
public final class HeightmapTerrainSampler implements TerrainSampler {
    private final float[] heightmap;
    private final BiomeType[] biomeGrid;
    private final int vertsX, vertsZ;
    private final float worldWidth, worldDepth;
    private final float halfW, halfD;

    public HeightmapTerrainSampler(float[] heightmap, BiomeType[] biomeGrid,
                                   int vertsX, int vertsZ, float worldWidth, float worldDepth) {
        this.heightmap = heightmap;
        this.biomeGrid = biomeGrid;
        this.vertsX = vertsX;
        this.vertsZ = vertsZ;
        this.worldWidth = worldWidth;
        this.worldDepth = worldDepth;
        this.halfW = worldWidth / 2f;
        this.halfD = worldDepth / 2f;
    }

    @Override
    public float heightAt(float worldX, float worldZ) {
        return TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, worldX, worldZ);
    }

    @Override
    public BiomeType biomeAt(float worldX, float worldZ) {
        int gx = clamp((int) ((worldX + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
        int gz = clamp((int) ((worldZ + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
        return biomeGrid[gz * vertsX + gx];
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
