package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;

public final class TerrainNoiseStack {
    private final GalaxyNoise continentNoise;
    private final GalaxyNoise ridgeNoise;
    private final GalaxyNoise detailNoise;

    public TerrainNoiseStack(long seed) {
        this.continentNoise = new GalaxyNoise(seed);
        this.ridgeNoise = new GalaxyNoise(seed + 1);
        this.detailNoise = new GalaxyNoise(seed + 2);
    }

    public float heightAt(Vector3 dir, BiomeMap biomeMap, int lod) {
        float cx = dir.x * 2f;
        float cy = dir.y * 2f;
        float continent = continentNoise.fbm(cx, cy, 6, 0.5f, 2.0f);

        float rx = dir.x * 8f;
        float ry = dir.y * 8f;
        float ridge = Math.abs(ridgeNoise.fbm(rx, ry, 5, 0.5f, 2.0f));

        float lat = CubeSphere.latitudeOf(dir);
        float lon = CubeSphere.longitudeOf(dir);
        BiomeType biome = biomeMap.getBiome(lat, lon, continent);
        float amplitude = biome.amplitude;
        float ridgeMix = biome.ridgeMix;

        float height = (continent + ridge * ridgeMix) * amplitude;

        if (lod >= 3) {
            float dx = dir.x * 64f;
            float dy = dir.y * 64f;
            float detail = detailNoise.fbm(dx, dy, 3, 0.5f, 2.0f);
            height += detail * amplitude * 0.1f;
        }

        return height;
    }
}
