package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainConformerTest {

    /** Water everywhere with x > 0; flat/dry elsewhere. */
    private TerrainSampler rightHalfWater() {
        return new TerrainSampler() {
            public float heightAt(float x, float z) { return 0f; }
            public boolean isWater(float x, float z) { return x > 0f; }
            public float slopeAt(float x, float z) { return 0f; }
        };
    }

    @Test
    void removesLotsMostlyOnWater() {
        List<BuildingLot> lots = new ArrayList<>();
        BuildingLot wet = new BuildingLot(new Rectangle(10, 0, 20, 20), DistrictType.RESIDENTIAL);
        BuildingLot dry = new BuildingLot(new Rectangle(-30, 0, 20, 20), DistrictType.RESIDENTIAL);
        lots.add(wet); lots.add(dry);
        List<Street> streets = new ArrayList<>();
        TerrainConformer.conform(streets, lots, rightHalfWater());
        assertTrue(lots.contains(dry));
        assertFalse(lots.contains(wet), "lot fully on water must be removed");
    }

    @Test
    void removesStreetsCrossingWater() {
        List<Street> streets = new ArrayList<>();
        Street wet = new Street(new Vector2(10, 0), new Vector2(40, 0), StreetTier.STREET);
        Street dry = new Street(new Vector2(-40, 0), new Vector2(-10, 0), StreetTier.STREET);
        streets.add(wet); streets.add(dry);
        TerrainConformer.conform(streets, new ArrayList<>(), rightHalfWater());
        assertTrue(streets.contains(dry));
        assertFalse(streets.contains(wet));
    }

    @Test
    void flatTerrainKeepsEverything() {
        List<BuildingLot> lots = new ArrayList<>();
        lots.add(new BuildingLot(new Rectangle(10, 0, 20, 20), DistrictType.RESIDENTIAL));
        List<Street> streets = new ArrayList<>();
        streets.add(new Street(new Vector2(0, 0), new Vector2(50, 0), StreetTier.AVENUE));
        TerrainConformer.conform(streets, lots, new FlatTerrainSampler());
        assertEquals(1, lots.size());
        assertEquals(1, streets.size());
    }
}
