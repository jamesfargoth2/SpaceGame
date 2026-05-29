package com.galacticodyssey.city.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlatTerrainSamplerTest {
    @Test
    void flatSamplerIsAlwaysBuildable() {
        TerrainSampler t = new FlatTerrainSampler();
        assertEquals(0f, t.heightAt(123f, -456f));
        assertFalse(t.isWater(123f, -456f));
        assertEquals(0f, t.slopeAt(123f, -456f));
    }
}
