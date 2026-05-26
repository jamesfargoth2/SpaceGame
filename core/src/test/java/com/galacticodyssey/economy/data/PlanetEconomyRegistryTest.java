package com.galacticodyssey.economy.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanetEconomyRegistryTest {
    private PlanetEconomyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlanetEconomyRegistry();
    }

    @Test
    void registerAndGetByPlanetId() {
        PlanetEconomyData data = makePlanet("planet_1", IndustryType.MINING, 50000);
        registry.register(data);

        PlanetEconomyData result = registry.get("planet_1");
        assertNotNull(result);
        assertEquals(IndustryType.MINING, result.industryType);
        assertEquals(50000, result.population);
    }

    @Test
    void getReturnsNullForUnknown() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void getAllReturnsCopy() {
        registry.register(makePlanet("planet_1", IndustryType.MINING, 50000));
        registry.register(makePlanet("planet_2", IndustryType.AGRICULTURAL, 200000));

        assertEquals(2, registry.getAll().size());
    }

    @Test
    void registerWithProductionsAndConsumptions() {
        PlanetEconomyData data = makePlanet("planet_1", IndustryType.MINING, 50000);

        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 20;
        data.productions.add(prod);

        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 10;
        data.consumptions.add(cons);

        data.childStationIds.add("station_1");

        registry.register(data);

        PlanetEconomyData result = registry.get("planet_1");
        assertEquals(1, result.productions.size());
        assertEquals("iron_ore", result.productions.get(0).commodityId);
        assertEquals(20, result.productions.get(0).outputPerTick);
        assertEquals(1, result.consumptions.size());
        assertEquals(1, result.childStationIds.size());
    }

    private PlanetEconomyData makePlanet(String id, IndustryType type, long population) {
        PlanetEconomyData data = new PlanetEconomyData();
        data.planetId = id;
        data.industryType = type;
        data.population = population;
        return data;
    }
}
