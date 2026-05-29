package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import java.util.EnumMap;
import java.util.Map;

/** Global grass tuning + per-biome grass settings. Loaded from data/flora/grass.json. */
public class GrassConfig {
    public float cellSize = 32f;
    public float radius = 140f;
    public float fadeBand = 24f;
    public float baseTuftsPerM2 = 0.25f;
    public int bladesPerTuft = 3;
    public int maxCachedCells = 256;
    public float windAmplitude = 0.18f;
    public float windFrequency = 1.3f;

    /** Per-biome grass settings; colours stored as unpacked float channels. */
    public static class BiomeGrass {
        public float density;
        public float heightMin, heightMax;
        public float colorAr, colorAg, colorAb;
        public float colorBr, colorBg, colorBb;
    }

    private final Map<BiomeType, BiomeGrass> biomes = new EnumMap<>(BiomeType.class);

    public void put(BiomeType biome, BiomeGrass g) { biomes.put(biome, g); }

    /** Returns null when the biome has no grass. */
    public BiomeGrass forBiome(BiomeType biome) { return biomes.get(biome); }
}
