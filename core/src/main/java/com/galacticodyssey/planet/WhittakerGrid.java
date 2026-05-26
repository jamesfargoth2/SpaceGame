package com.galacticodyssey.planet;

public final class WhittakerGrid {
    private static final float TEMP_FREEZING = 253f;
    private static final float TEMP_COOL = 273f;
    private static final float TEMP_WARM = 303f;

    private static final float MOISTURE_ARID = 0.25f;
    private static final float MOISTURE_DRY = 0.50f;
    private static final float MOISTURE_MOIST = 0.75f;

    private static final BiomeType[][] GRID = {
        // [moisture row][temperature col]: Freezing, Cool, Warm, Hot
        { BiomeType.ICE_FIELD,     BiomeType.ROCKY_WASTE,      BiomeType.DESERT,           BiomeType.VOLCANIC },
        { BiomeType.POLAR_DESERT,  BiomeType.STEPPE,           BiomeType.ARID_SHRUB,       BiomeType.BADLANDS },
        { BiomeType.TUNDRA,        BiomeType.TEMPERATE_FOREST, BiomeType.GRASSLAND,        BiomeType.SAVANNA },
        { BiomeType.ICE_SHEET,     BiomeType.BOREAL_FOREST,    BiomeType.TROPICAL_FOREST,  BiomeType.SWAMP },
    };

    private WhittakerGrid() {}

    public static BiomeType classify(float temperatureK, float moisture) {
        int tempIdx;
        if (temperatureK < TEMP_FREEZING) tempIdx = 0;
        else if (temperatureK < TEMP_COOL) tempIdx = 1;
        else if (temperatureK < TEMP_WARM) tempIdx = 2;
        else tempIdx = 3;

        int moistIdx;
        if (moisture < MOISTURE_ARID) moistIdx = 0;
        else if (moisture < MOISTURE_DRY) moistIdx = 1;
        else if (moisture < MOISTURE_MOIST) moistIdx = 2;
        else moistIdx = 3;

        return GRID[moistIdx][tempIdx];
    }
}
