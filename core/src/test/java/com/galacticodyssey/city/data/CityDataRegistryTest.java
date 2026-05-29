package com.galacticodyssey.city.data;

import com.galacticodyssey.city.layout.model.BuildingFunction;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CityDataRegistryTest {
    private CityDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CityDataRegistry();
        registry.loadFromClasspath();
    }

    @Test
    void allSevenTiersLoaded() {
        assertEquals(7, registry.sizeTiers().size());
    }

    @Test
    void tierLookupByPopulationPicksCorrectTier() {
        assertEquals("OUTPOST", registry.tierForPopulation(10).type);
        assertEquals("CITY", registry.tierForPopulation(30000).type);
        assertEquals("LARGE_METROPOLIS", registry.tierForPopulation(2_000_000).type);
    }

    @Test
    void districtMixHasFunctionsAndLotSizes() {
        DistrictMixDef commercial = registry.districtMix(DistrictType.COMMERCIAL);
        assertNotNull(commercial);
        assertTrue(commercial.minLot < commercial.maxLot);
        assertTrue(commercial.functions.stream()
                .anyMatch(fw -> fw.function == BuildingFunction.SHOP));
    }

    @Test
    void factionFormBiasReturnsForms() {
        List<CityForm> forms = registry.factionFormBias(FactionEthos.ISOLATIONIST);
        assertTrue(forms.contains(CityForm.ORGANIC));
    }
}
