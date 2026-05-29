package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitySizeProfileTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new CityDataRegistry();
        reg.loadFromClasspath();
    }

    @Test
    void mapsPopulationToTypeAndRadiusBand() {
        CitySizeProfile p = CitySizeProfile.from(reg, 30000, 99L);
        assertEquals(CityType.CITY, p.type);
        assertTrue(p.radiusMetres >= 600f && p.radiusMetres <= 800f);
        assertTrue(p.hasWall);
        assertTrue(p.density > 0f && p.density <= 1f);
    }

    @Test
    void deterministicForSameSeed() {
        CitySizeProfile a = CitySizeProfile.from(reg, 30000, 99L);
        CitySizeProfile b = CitySizeProfile.from(reg, 30000, 99L);
        assertEquals(a.radiusMetres, b.radiusMetres);
        assertEquals(a.hasWall, b.hasWall);
    }

    @Test
    void biggerPopulationNeverShrinksRadius() {
        int[] pops = {10, 400, 4000, 30000, 80000, 250000, 1_000_000};
        float prev = -1f;
        for (int pop : pops) {
            float r = CitySizeProfile.from(reg, pop, 7L).radiusMetres;
            assertTrue(r >= prev, "radius should be monotonic non-decreasing with population");
            prev = r;
        }
    }

    @Test
    void outpostHasNoWall() {
        assertFalse(CitySizeProfile.from(reg, 10, 1L).hasWall);
    }
}
