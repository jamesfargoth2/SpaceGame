package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityWall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WallBuilderTest {

    private List<CityBlock> diskOfBlocks(float radius, float step) {
        List<CityBlock> blocks = new ArrayList<>();
        for (float x = -radius; x <= radius; x += step) {
            for (float y = -radius; y <= radius; y += step) {
                if (Math.sqrt(x * x + y * y) <= radius) {
                    blocks.add(new CityBlock(new Rectangle(x - 5, y - 5, 10, 10)));
                }
            }
        }
        return blocks;
    }

    @Test
    void wallHullEnclosesAllBlockCentroids() {
        List<CityBlock> blocks = diskOfBlocks(200f, 40f);
        CityWall wall = WallBuilder.build(blocks);
        assertNotNull(wall);
        assertTrue(wall.hull.size() >= 3, "hull needs at least 3 vertices");
        float maxHull = 0f;
        for (com.badlogic.gdx.math.Vector2 v : wall.hull) maxHull = Math.max(maxHull, v.len());
        for (CityBlock b : blocks) assertTrue(b.centroid().len() <= maxHull + 0.001f);
    }

    @Test
    void producesAtLeastOneGate() {
        CityWall wall = WallBuilder.build(diskOfBlocks(200f, 40f));
        assertFalse(wall.gates.isEmpty());
    }

    @Test
    void deterministic() {
        CityWall a = WallBuilder.build(diskOfBlocks(200f, 40f));
        CityWall b = WallBuilder.build(diskOfBlocks(200f, 40f));
        assertEquals(a.hull.size(), b.hull.size());
        assertEquals(a.gates.size(), b.gates.size());
    }
}
