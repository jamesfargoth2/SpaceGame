package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityGate;
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

    private List<com.galacticodyssey.city.layout.model.Street> crossAvenues(float radius) {
        List<com.galacticodyssey.city.layout.model.Street> s = new java.util.ArrayList<>();
        s.add(new com.galacticodyssey.city.layout.model.Street(
                new com.badlogic.gdx.math.Vector2(0, -radius * 2), new com.badlogic.gdx.math.Vector2(0, radius * 2),
                com.galacticodyssey.city.layout.model.StreetTier.AVENUE));     // vertical avenue at x=0
        s.add(new com.galacticodyssey.city.layout.model.Street(
                new com.badlogic.gdx.math.Vector2(-radius * 2, 0), new com.badlogic.gdx.math.Vector2(radius * 2, 0),
                com.galacticodyssey.city.layout.model.StreetTier.AVENUE));     // horizontal avenue at y=0
        s.add(new com.galacticodyssey.city.layout.model.Street(
                new com.badlogic.gdx.math.Vector2(-radius*2, radius*2), new com.badlogic.gdx.math.Vector2(radius*2, radius*2),
                com.galacticodyssey.city.layout.model.StreetTier.STREET));     // a non-avenue, must be ignored
        return s;
    }

    private float distanceToHullBoundary(List<Vector2> hull, Vector2 p) {
        float minDist = Float.MAX_VALUE;
        for (int i = 0; i < hull.size(); i++) {
            Vector2 a = hull.get(i);
            Vector2 b = hull.get((i + 1) % hull.size());
            float d = Intersector.distanceSegmentPoint(a.x, a.y, b.x, b.y, p.x, p.y);
            if (d < minDist) minDist = d;
        }
        return minDist;
    }

    @Test
    void wallHullEnclosesAllBlockCentroids() {
        List<CityBlock> blocks = diskOfBlocks(200f, 40f);
        CityWall wall = WallBuilder.build(blocks, crossAvenues(200f));
        assertNotNull(wall);
        assertTrue(wall.hull.size() >= 3, "hull needs at least 3 vertices");
        float maxHull = 0f;
        for (com.badlogic.gdx.math.Vector2 v : wall.hull) maxHull = Math.max(maxHull, v.len());
        for (CityBlock b : blocks) assertTrue(b.centroid().len() <= maxHull + 0.001f);
    }

    @Test
    void gatesLieOnAvenuesWherePiercingTheHull() {
        List<CityBlock> blocks = diskOfBlocks(200f, 40f);
        CityWall wall = WallBuilder.build(blocks, crossAvenues(200f));
        assertFalse(wall.gates.isEmpty(), "avenues piercing the wall should cut gates");
        for (CityGate g : wall.gates) {
            boolean onAvenue = Math.abs(g.position.x) <= 0.5f || Math.abs(g.position.y) <= 0.5f;
            assertTrue(onAvenue, "every gate must sit on an avenue line, was " + g.position);
            assertTrue(distanceToHullBoundary(wall.hull, g.position) <= 0.5f,
                    "every gate must sit on the wall hull, was " + g.position);
        }
    }

    @Test
    void deterministic() {
        CityWall a = WallBuilder.build(diskOfBlocks(200f, 40f), crossAvenues(200f));
        CityWall b = WallBuilder.build(diskOfBlocks(200f, 40f), crossAvenues(200f));
        assertEquals(a.hull.size(), b.hull.size());
        assertEquals(a.gates.size(), b.gates.size());
    }
}
