package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.Street;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreetNetworkBuilderTest {

    @Test
    void producesBlocksAndStreetsWithinRadius() {
        StreetNetwork n = StreetNetworkBuilder.build(CityForm.GRID, 300f, 0.5f, 1L);
        assertFalse(n.blocks.isEmpty());
        assertFalse(n.streets.isEmpty());
        for (CityBlock b : n.blocks) {
            assertTrue(b.centroid().len() <= 300f + 1f, "block centroid within radius");
        }
    }

    @Test
    void deterministicBlockCount() {
        int a = StreetNetworkBuilder.build(CityForm.ORGANIC, 300f, 0.5f, 2L).blocks.size();
        int b = StreetNetworkBuilder.build(CityForm.ORGANIC, 300f, 0.5f, 2L).blocks.size();
        assertEquals(a, b);
    }

    @Test
    void biggerRadiusYieldsMoreBlocks() {
        int small = StreetNetworkBuilder.build(CityForm.GRID, 150f, 0.5f, 3L).blocks.size();
        int big = StreetNetworkBuilder.build(CityForm.GRID, 600f, 0.5f, 3L).blocks.size();
        assertTrue(big > small);
    }

    @Test
    void higherDensityYieldsMoreBlocksAtSameRadius() {
        int sparse = StreetNetworkBuilder.build(CityForm.GRID, 400f, 0.2f, 4L).blocks.size();
        int dense = StreetNetworkBuilder.build(CityForm.GRID, 400f, 0.9f, 4L).blocks.size();
        assertTrue(dense > sparse, "denser city packs more (smaller) blocks");
    }

    @Test
    void linearFormIsElongated() {
        StreetNetwork n = StreetNetworkBuilder.build(CityForm.LINEAR, 400f, 0.5f, 5L);
        float maxX = 0f, maxY = 0f;
        for (CityBlock b : n.blocks) {
            maxX = Math.max(maxX, Math.abs(b.centroid().x));
            maxY = Math.max(maxY, Math.abs(b.centroid().y));
        }
        assertTrue(maxX > maxY * 1.5f, "LINEAR cities extend further along X than Y");
    }

    @Test
    void blocksDoNotOverlap() {
        StreetNetwork n = StreetNetworkBuilder.build(CityForm.GRID, 300f, 0.6f, 6L);
        for (int i = 0; i < n.blocks.size(); i++) {
            for (int j = i + 1; j < n.blocks.size(); j++) {
                assertFalse(n.blocks.get(i).footprint.overlaps(n.blocks.get(j).footprint),
                        "blocks must not overlap");
            }
        }
    }
}
