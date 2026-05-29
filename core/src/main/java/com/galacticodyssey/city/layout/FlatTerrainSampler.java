package com.galacticodyssey.city.layout;

public final class FlatTerrainSampler implements TerrainSampler {
    @Override public float heightAt(float x, float z) { return 0f; }
    @Override public boolean isWater(float x, float z) { return false; }
    @Override public float slopeAt(float x, float z) { return 0f; }
}
