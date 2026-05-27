package com.galacticodyssey.planet;

public enum BiomeType {
    ICE_SHEET(0.1f, 0.15f),
    TUNDRA(0.25f, 0.2f),
    POLAR_DESERT(0.2f, 0.15f),
    ICE_FIELD(0.15f, 0.2f),
    BOREAL_FOREST(0.35f, 0.4f),
    TEMPERATE_FOREST(0.3f, 0.35f),
    STEPPE(0.2f, 0.1f),
    ROCKY_WASTE(0.5f, 0.8f),
    TROPICAL_FOREST(0.3f, 0.3f),
    GRASSLAND(0.2f, 0.1f),
    ARID_SHRUB(0.2f, 0.15f),
    DESERT(0.15f, 0.05f),
    SWAMP(0.1f, 0.05f),
    SAVANNA(0.2f, 0.15f),
    BADLANDS(0.45f, 0.7f),
    VOLCANIC(0.6f, 0.9f),
    OCEAN(0.05f, 0.1f),
    LAKE(0.05f, 0.05f),
    RIVER(0.02f, 0.0f);

    public final float amplitude;
    public final float ridgeMix;

    BiomeType(float amplitude, float ridgeMix) {
        this.amplitude = amplitude;
        this.ridgeMix = ridgeMix;
    }
}
