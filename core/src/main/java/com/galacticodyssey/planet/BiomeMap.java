package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.planet.climate.ClimateData;
import java.util.EnumSet;

public final class BiomeMap {
    public final long seed;
    public final float seaLevel;
    public final float snowLine;
    public final float baseMoisture;
    public final float surfaceTemp;
    public final EnumSet<BiomeType> allowedBiomes;
    private final GalaxyNoise moistureNoise;
    private final ClimateData climate;

    public BiomeMap(long seed, float seaLevel, float snowLine, float baseMoisture,
                    float surfaceTemp, EnumSet<BiomeType> allowedBiomes) {
        this(seed, seaLevel, snowLine, baseMoisture, surfaceTemp, allowedBiomes, null);
    }

    public BiomeMap(long seed, float seaLevel, float snowLine, float baseMoisture,
                    float surfaceTemp, EnumSet<BiomeType> allowedBiomes, ClimateData climate) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.snowLine = snowLine;
        this.baseMoisture = baseMoisture;
        this.surfaceTemp = surfaceTemp;
        this.allowedBiomes = allowedBiomes;
        this.moistureNoise = new GalaxyNoise(seed);
        this.climate = climate;
    }

    public float getTemperature(float latRadians, float lonRadians) {
        if (climate != null) {
            return climate.sampleTemperature(lonRadians, latRadians);
        }
        float sinLat = (float) Math.sin(latRadians);
        return surfaceTemp * (1.0f - 0.4f * sinLat * sinLat);
    }

    public float getMoisture(float latRadians, float lonRadians) {
        if (climate != null) {
            return climate.samplePrecipitation(lonRadians, latRadians);
        }
        float noise = moistureNoise.fbm(
            (float)(lonRadians * 2.0), (float)(latRadians * 2.0), 4, 0.5f, 2.0f);
        return Math.max(0f, Math.min(1f, baseMoisture + noise * 0.3f));
    }

    public BiomeType getBiome(float latRadians, float lonRadians, float elevation) {
        float temp = getTemperature(latRadians, lonRadians);
        float moisture = getMoisture(latRadians, lonRadians);

        if (climate == null) {
            temp -= elevation * 6.5f / 1000f;
            moisture -= elevation * 0.1f;
            moisture = Math.max(0f, Math.min(1f, moisture));
        }

        if (elevation < seaLevel && temp > 273f) return filterBiome(BiomeType.OCEAN);
        if (elevation < seaLevel && temp <= 273f) return filterBiome(BiomeType.ICE_SHEET);
        if (elevation > snowLine) return filterBiome(BiomeType.ICE_FIELD);

        BiomeType biome = WhittakerGrid.classify(temp, moisture);
        return filterBiome(biome);
    }

    private BiomeType filterBiome(BiomeType biome) {
        if (allowedBiomes.contains(biome)) return biome;
        for (BiomeType allowed : allowedBiomes) return allowed;
        return BiomeType.ROCKY_WASTE;
    }
}
