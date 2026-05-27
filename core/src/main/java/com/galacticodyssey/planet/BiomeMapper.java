package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.climate.ClimateData;
import com.galacticodyssey.planet.climate.ClimateSimulator;
import java.util.EnumSet;
import java.util.Random;

public final class BiomeMapper {

    public BiomeMap generate(Planet planet, Atmosphere atmosphere) {
        return generate(planet, atmosphere, (lon, lat) -> 0f);
    }

    public BiomeMap generate(Planet planet, Atmosphere atmosphere, HeightSampler heightSampler) {
        long biomeSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.BIOME_DOMAIN), 0);
        Random rng = new Random(biomeSeed);

        float seaLevel = rng.nextFloat() * 0.3f;
        float snowLine = 0.6f + rng.nextFloat() * 0.3f;
        float baseMoisture = moistureFromType(planet.type, rng);
        float surfaceTemp = atmosphere != null ? atmosphere.surfaceTemp : 200f;
        EnumSet<BiomeType> allowed = allowedBiomesForType(planet.type);

        long climateSeed = SeedDeriver.domain(biomeSeed, SeedDeriver.CLIMATE_DOMAIN);
        ClimateSimulator simulator = new ClimateSimulator(climateSeed);
        ClimateData climate = simulator.simulate(planet, atmosphere, heightSampler);

        return new BiomeMap(biomeSeed, seaLevel, snowLine, baseMoisture, surfaceTemp, allowed, climate);
    }

    private float moistureFromType(PlanetType type, Random rng) {
        return switch (type) {
            case OCEAN -> 0.8f + rng.nextFloat() * 0.2f;
            case TERRAN -> 0.3f + rng.nextFloat() * 0.4f;
            case ARID -> 0.05f + rng.nextFloat() * 0.15f;
            case TOXIC -> 0.05f + rng.nextFloat() * 0.1f;
            case ICE_WORLD -> 0.2f + rng.nextFloat() * 0.3f;
            case BARREN -> 0.01f + rng.nextFloat() * 0.05f;
            case MOLTEN -> 0.0f;
            default -> 0.3f;
        };
    }

    private EnumSet<BiomeType> allowedBiomesForType(PlanetType type) {
        return switch (type) {
            case TERRAN -> EnumSet.allOf(BiomeType.class);
            case OCEAN -> EnumSet.of(BiomeType.OCEAN, BiomeType.ICE_SHEET, BiomeType.SWAMP, BiomeType.TROPICAL_FOREST);
            case ARID -> EnumSet.of(BiomeType.DESERT, BiomeType.ARID_SHRUB, BiomeType.BADLANDS, BiomeType.ROCKY_WASTE, BiomeType.STEPPE, BiomeType.POLAR_DESERT);
            case TOXIC -> EnumSet.of(BiomeType.VOLCANIC, BiomeType.BADLANDS, BiomeType.ROCKY_WASTE);
            case ICE_WORLD -> EnumSet.of(BiomeType.ICE_SHEET, BiomeType.ICE_FIELD, BiomeType.POLAR_DESERT, BiomeType.TUNDRA);
            case BARREN -> EnumSet.of(BiomeType.ROCKY_WASTE, BiomeType.DESERT, BiomeType.POLAR_DESERT);
            case MOLTEN -> EnumSet.of(BiomeType.VOLCANIC);
            default -> EnumSet.of(BiomeType.ROCKY_WASTE);
        };
    }
}
