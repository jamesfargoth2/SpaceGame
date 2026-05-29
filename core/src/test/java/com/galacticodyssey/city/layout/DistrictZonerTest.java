package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistrictZonerTest {

    private List<CityBlock> ringOfBlocks(int n, float radius) {
        List<CityBlock> blocks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float a = (float) (i * 2 * Math.PI / n);
            float x = (float) Math.cos(a) * radius, y = (float) Math.sin(a) * radius;
            blocks.add(new CityBlock(new Rectangle(x - 10, y - 10, 20, 20)));
        }
        return blocks;
    }

    @Test
    void everyBlockGetsANonNullDistrict() {
        List<CityBlock> blocks = ringOfBlocks(12, 200f);
        blocks.add(new CityBlock(new Rectangle(-10, -10, 20, 20))); // centre
        DistrictZoner.zone(blocks, new ArrayList<>(), 700f, 1L);
        for (CityBlock b : blocks) assertNotNull(b.district);
    }

    @Test
    void centreBlockIsGovernment() {
        List<CityBlock> blocks = new ArrayList<>();
        CityBlock centre = new CityBlock(new Rectangle(-10, -10, 20, 20));
        blocks.add(centre);
        DistrictZoner.zone(blocks, new ArrayList<>(), 700f, 2L);
        assertEquals(DistrictType.GOVERNMENT, centre.district);
    }

    @Test
    void blockAdjacentToSpaceportLandmarkBecomesSpaceport() {
        CityBlock near = new CityBlock(new Rectangle(495, -5, 20, 20)); // centroid ~ (505,5)
        List<CityBlock> blocks = new ArrayList<>();
        blocks.add(near);
        List<Landmark> lm = new ArrayList<>();
        lm.add(new Landmark(LandmarkType.SPACEPORT, new Vector2(505, 5), false));
        DistrictZoner.zone(blocks, lm, 700f, 3L);
        assertEquals(DistrictType.SPACEPORT, near.district);
    }

    @Test
    void deterministic() {
        List<CityBlock> a = ringOfBlocks(20, 300f);
        List<CityBlock> b = ringOfBlocks(20, 300f);
        DistrictZoner.zone(a, new ArrayList<>(), 700f, 7L);
        DistrictZoner.zone(b, new ArrayList<>(), 700f, 7L);
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).district, b.get(i).district);
    }
}
