package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.DistrictType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotSubdividerTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() { reg = new CityDataRegistry(); reg.loadFromClasspath(); }

    @Test
    void largeCommercialBlockSplitsIntoMultipleLots() {
        CityBlock block = new CityBlock(new Rectangle(0, 0, 60, 60)); // 3600 m^2
        block.district = DistrictType.COMMERCIAL;                     // maxLot 250
        List<CityBlock> blocks = new ArrayList<>();
        blocks.add(block);
        List<BuildingLot> lots = LotSubdivider.subdivide(blocks, reg, 1L);
        assertTrue(lots.size() > 1, "block much bigger than maxLot should split");
        for (BuildingLot lot : lots) assertEquals(DistrictType.COMMERCIAL, lot.district);
    }

    @Test
    void lotsStayWithinTheirBlock() {
        CityBlock block = new CityBlock(new Rectangle(10, 20, 80, 40));
        block.district = DistrictType.RESIDENTIAL;
        List<CityBlock> blocks = new ArrayList<>();
        blocks.add(block);
        for (BuildingLot lot : LotSubdivider.subdivide(blocks, reg, 2L)) {
            assertTrue(lot.footprint.x >= 10 - 0.001f);
            assertTrue(lot.footprint.y >= 20 - 0.001f);
            assertTrue(lot.footprint.x + lot.footprint.width <= 90 + 0.001f);
            assertTrue(lot.footprint.y + lot.footprint.height <= 60 + 0.001f);
        }
    }

    @Test
    void deterministic() {
        CityBlock b1 = new CityBlock(new Rectangle(0, 0, 100, 100));
        b1.district = DistrictType.INDUSTRIAL;
        CityBlock b2 = new CityBlock(new Rectangle(0, 0, 100, 100));
        b2.district = DistrictType.INDUSTRIAL;
        List<CityBlock> l1 = new ArrayList<>(); l1.add(b1);
        List<CityBlock> l2 = new ArrayList<>(); l2.add(b2);
        assertEquals(LotSubdivider.subdivide(l1, reg, 5L).size(),
                     LotSubdivider.subdivide(l2, reg, 5L).size());
    }
}
