package com.galacticodyssey.city.layout;

/** Local-planar terrain queries injected into city generation. Real planet hookup
 *  is sub-project E; A ships {@link FlatTerrainSampler} for tests and standalone use. */
public interface TerrainSampler {
    float heightAt(float localX, float localZ);   // metres
    boolean isWater(float localX, float localZ);
    float slopeAt(float localX, float localZ);     // 0..1 (tan of slope angle)
}
