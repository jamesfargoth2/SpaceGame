package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelPojoTest {
    @Test
    void blockCentroidIsRectangleCentre() {
        CityBlock b = new CityBlock(new Rectangle(10, 20, 40, 60));
        assertEquals(new Vector2(30, 50), b.centroid());
    }

    @Test
    void lotCarriesDistrictAndFunction() {
        BuildingLot lot = new BuildingLot(new Rectangle(0, 0, 10, 10), DistrictType.COMMERCIAL);
        lot.function = BuildingFunction.SHOP;
        assertEquals(DistrictType.COMMERCIAL, lot.district);
        assertEquals(BuildingFunction.SHOP, lot.function);
    }

    @Test
    void galaxyAnchorDefaultsToIdentity() {
        GalaxyAnchor a = new GalaxyAnchor();
        assertFalse(a.assigned, "A leaves the anchor unassigned for E to fill");
    }
}
