package com.galacticodyssey.city.layout.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnumsTest {
    @Test
    void cityTypeHasSevenTiersInPopulationOrder() {
        CityType[] v = CityType.values();
        assertEquals(7, v.length);
        assertEquals(CityType.OUTPOST, v[0]);
        assertEquals(CityType.LARGE_METROPOLIS, v[6]);
    }

    @Test
    void enumsContainExpectedMembers() {
        assertNotNull(CityForm.valueOf("RADIAL"));
        assertNotNull(StreetTier.valueOf("AVENUE"));
        assertNotNull(DistrictType.valueOf("GOVERNMENT"));
        assertNotNull(DistrictType.valueOf("SLUMS"));
        assertNotNull(BuildingFunction.valueOf("FACTION_HQ"));
        assertNotNull(BuildingFunction.valueOf("CANTINA"));
        assertNotNull(LandmarkType.valueOf("SPACEPORT"));
    }
}
