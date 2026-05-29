package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;

/** Abstracts terrain queries so grass generation is independent of how terrain is stored.
 *  Today: a heightmap+biomeGrid adapter. Later: a streamed-terrain adapter (no grass changes). */
public interface TerrainSampler {
    float heightAt(float worldX, float worldZ);
    BiomeType biomeAt(float worldX, float worldZ);
}
