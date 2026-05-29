package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotFunctionAssignerTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() { reg = new CityDataRegistry(); reg.loadFromClasspath(); }

    @Test
    void everyLotGetsAFunctionFromItsDistrictMix() {
        List<BuildingLot> lots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lots.add(new BuildingLot(new Rectangle(i * 30, 0, 20, 20), DistrictType.RESIDENTIAL));
        }
        LotFunctionAssigner.assign(lots, new ArrayList<>(), reg, 1L);
        for (BuildingLot lot : lots) {
            assertNotEquals(BuildingFunction.EMPTY_LOT, lot.function);
            assertTrue(lot.function == BuildingFunction.HOUSE
                    || lot.function == BuildingFunction.APARTMENT
                    || lot.function == BuildingFunction.SHOP);
        }
    }

    @Test
    void factionLandmarkLotBecomesFactionHq() {
        BuildingLot lot = new BuildingLot(new Rectangle(95, 95, 20, 20), DistrictType.GOVERNMENT);
        List<BuildingLot> lots = new ArrayList<>();
        lots.add(lot);
        List<Landmark> lm = new ArrayList<>();
        lm.add(new Landmark(LandmarkType.FACTION_LANDMARK, new Vector2(105, 105), false)); // inside lot
        LotFunctionAssigner.assign(lots, lm, reg, 2L);
        assertEquals(BuildingFunction.FACTION_HQ, lot.function);
    }

    @Test
    void deterministic() {
        List<BuildingLot> a = new ArrayList<>();
        List<BuildingLot> b = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            a.add(new BuildingLot(new Rectangle(i * 30, 0, 20, 20), DistrictType.COMMERCIAL));
            b.add(new BuildingLot(new Rectangle(i * 30, 0, 20, 20), DistrictType.COMMERCIAL));
        }
        LotFunctionAssigner.assign(a, new ArrayList<>(), reg, 9L);
        LotFunctionAssigner.assign(b, new ArrayList<>(), reg, 9L);
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).function, b.get(i).function);
    }
}
