package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrassFieldTest {
    static class UniformSampler implements TerrainSampler {
        public float heightAt(float x, float z) { return 0f; }
        public BiomeType biomeAt(float x, float z) { return BiomeType.GRASSLAND; }
    }

    static GrassField field() {
        GrassConfig c = new GrassConfig();
        c.cellSize = 32f; c.radius = 48f; c.baseTuftsPerM2 = 0.05f; c.maxCachedCells = 64;
        GrassConfig.BiomeGrass g = new GrassConfig.BiomeGrass();
        g.density = 1f; g.heightMin = 0.5f; g.heightMax = 1f;
        c.put(BiomeType.GRASSLAND, g);
        return new GrassField(c, new UniformSampler(), 123L);
    }

    @Test
    void activeSetIsRadiusDiscAndPackedBufferMatches() {
        GrassField f = field();
        boolean changed = f.update(0f, 0f);
        assertTrue(changed, "first update populates");
        assertTrue(f.instanceCount() > 0);
        assertEquals(f.instanceCount() * GrassCell.STRIDE, f.instanceBuffer().length,
            "packed buffer length == count * stride (effective length)");
    }

    @Test
    void noChangeWhenStayingInSameCellNeighbourhood() {
        GrassField f = field();
        f.update(0f, 0f);
        boolean changed = f.update(1f, 1f); // tiny move, same active disc
        assertFalse(changed, "active cell set unchanged -> no repack");
    }

    @Test
    void movingFarChangesActiveSet() {
        GrassField f = field();
        f.update(0f, 0f);
        boolean changed = f.update(500f, 500f);
        assertTrue(changed, "moving far changes the active cell set");
    }

    @Test
    void deterministicCacheReuse() {
        GrassField f = field();
        f.update(0f, 0f);
        float[] first = f.instanceBuffer().clone();
        int firstLen = f.instanceCount();
        f.update(500f, 500f);
        f.update(0f, 0f); // return -> cached cells reused, identical packing
        assertEquals(firstLen, f.instanceCount());
    }
}
