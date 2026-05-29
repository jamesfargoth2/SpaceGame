package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.BuildingFunction;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityLayout;
import com.galacticodyssey.city.layout.model.CityType;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityLayoutGeneratorTest {
    private CityLayoutGenerator gen;

    @BeforeEach
    void setUp() {
        CityDataRegistry reg = new CityDataRegistry();
        reg.loadFromClasspath();
        gen = new CityLayoutGenerator(reg);
    }

    private CityRequest req(int population, long seed) {
        CityRequest r = new CityRequest();
        r.population = population;
        r.seed = seed;
        r.rulingEthos = FactionEthos.FEDERATION;
        r.factionId = "fed";
        return r;
    }

    @Test
    void deterministicEndToEnd() {
        CityLayout a = gen.generate(req(30000, 123L));
        CityLayout b = gen.generate(req(30000, 123L));
        assertEquals(a.type, b.type);
        assertEquals(a.form, b.form);
        assertEquals(a.name, b.name);
        assertEquals(a.lots.size(), b.lots.size());
        assertEquals(a.blocks.size(), b.blocks.size());
        assertEquals(a.streets.size(), b.streets.size());
        for (int i = 0; i < a.lots.size(); i++) {
            assertEquals(a.lots.get(i).function, b.lots.get(i).function);
            assertEquals(a.lots.get(i).district, b.lots.get(i).district);
        }
    }

    @Test
    void cityTypeMatchesPopulation() {
        assertEquals(CityType.OUTPOST, gen.generate(req(10, 1L)).type);
        assertEquals(CityType.CITY, gen.generate(req(30000, 1L)).type);
        assertEquals(CityType.LARGE_METROPOLIS, gen.generate(req(2_000_000, 1L)).type);
    }

    @Test
    void biggerPopulationProducesMoreLots() {
        int town = gen.generate(req(400, 5L)).lots.size();
        int city = gen.generate(req(30000, 5L)).lots.size();
        assertTrue(city > town, "a CITY should have far more lots than a FRONTIER_TOWN");
    }

    @Test
    void everyLotHasDistrictAndFunction() {
        CityLayout layout = gen.generate(req(30000, 7L));
        assertFalse(layout.lots.isEmpty());
        for (BuildingLot lot : layout.lots) {
            assertNotNull(lot.district);
            assertNotNull(lot.function);
        }
    }

    @Test
    void walledTierHasWallAndOutpostDoesNot() {
        assertNotNull(gen.generate(req(30000, 9L)).wall, "CITY tier is walled");
        assertNull(gen.generate(req(10, 9L)).wall, "OUTPOST has no wall");
    }

    @Test
    void anchorLeftUnassignedForSubProjectE() {
        assertFalse(gen.generate(req(30000, 9L)).localToGalaxyAnchor.assigned);
    }

    @Test
    void nameIsNonEmpty() {
        assertNotNull(gen.generate(req(30000, 11L)).name);
        assertFalse(gen.generate(req(30000, 11L)).name.isEmpty());
    }
}
