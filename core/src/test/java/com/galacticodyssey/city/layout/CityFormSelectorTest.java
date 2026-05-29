package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityFormSelectorTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() { reg = new CityDataRegistry(); reg.loadFromClasspath(); }

    @Test
    void deterministicForSameSeed() {
        CitySizeProfile p = CitySizeProfile.from(reg, 30000, 5L);
        CityForm a = CityFormSelector.select(reg, FactionEthos.CORPORATE, p, 5L);
        CityForm b = CityFormSelector.select(reg, FactionEthos.CORPORATE, p, 5L);
        assertEquals(a, b);
    }

    @Test
    void choiceComesFromTierOrFactionBias() {
        CitySizeProfile p = CitySizeProfile.from(reg, 80000, 11L); // LARGE_CITY: tier bias RADIAL
        CityForm form = CityFormSelector.select(reg, FactionEthos.ISOLATIONIST, p, 11L);
        // Candidate pool = tier formBias (RADIAL) + faction bias (ORGANIC, SPRAWL)
        assertTrue(form == CityForm.RADIAL || form == CityForm.ORGANIC || form == CityForm.SPRAWL);
    }

    @Test
    void alwaysReturnsAFormEvenWithEmptyFactionBias() {
        CitySizeProfile p = CitySizeProfile.from(reg, 10, 3L); // OUTPOST: LINEAR/SPRAWL
        CityForm form = CityFormSelector.select(reg, FactionEthos.CORPORATE, p, 3L);
        assertNotNull(form);
    }
}
