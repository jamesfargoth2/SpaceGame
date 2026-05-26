package com.galacticodyssey.economy.simulation;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.data.IndustryType;
import com.galacticodyssey.economy.data.PlanetEconomyData;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import com.galacticodyssey.economy.events.ShortageEvent;
import com.galacticodyssey.economy.events.SurplusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanetaryEconomyManagerTest {
    private EventBus eventBus;
    private PlanetEconomyRegistry planetRegistry;
    private PlanetaryEconomyManager manager;

    private final List<ProductionTickEvent> tickEvents = new ArrayList<>();
    private final List<ShortageEvent> shortageEvents = new ArrayList<>();
    private final List<SurplusEvent> surplusEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        planetRegistry = new PlanetEconomyRegistry();

        eventBus.subscribe(ProductionTickEvent.class, tickEvents::add);
        eventBus.subscribe(ShortageEvent.class, shortageEvents::add);
        eventBus.subscribe(SurplusEvent.class, surplusEvents::add);
    }

    private PlanetEconomyData makePlanet(String id, String stationId) {
        PlanetEconomyData data = new PlanetEconomyData();
        data.planetId = id;
        data.population = 100000;
        data.industryType = IndustryType.MINING;
        data.childStationIds.add(stationId);
        return data;
    }

    @Test
    void tickProducesPositiveDeltas() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 20;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("iron_ore", 50);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("iron_ore", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, tickEvents.size());
        ProductionTickEvent evt = tickEvents.get(0);
        assertEquals("p1", evt.planetId);
        assertTrue(evt.stationDeltas.containsKey("s1"));
        assertEquals(20, evt.stationDeltas.get("s1").get("iron_ore"));
    }

    @Test
    void tickConsumesWithNegativeDeltas() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 10;
        planet.consumptions.add(cons);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("food_rations", 50);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("food_rations", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, tickEvents.size());
        assertEquals(-10, tickEvents.get(0).stationDeltas.get("s1").get("food_rations"));
    }

    @Test
    void shortageEventWhenStockTooLow() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 30;
        planet.consumptions.add(cons);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("food_rations", 10);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("food_rations", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, shortageEvents.size());
        assertEquals("food_rations", shortageEvents.get(0).commodityId);
        assertEquals(20, shortageEvents.get(0).deficit);

        assertEquals(-10, tickEvents.get(0).stationDeltas.get("s1").get("food_rations"));
    }

    @Test
    void surplusEventWhenProductionOverflows() {
        PlanetEconomyData planet = makePlanet("p1", "s1");
        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 50;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        Map<String, Integer> stationStocks = new HashMap<>();
        stationStocks.put("iron_ore", 190);
        Map<String, Integer> stationMaxStocks = new HashMap<>();
        stationMaxStocks.put("iron_ore", 200);
        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", stationStocks);
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", stationMaxStocks);

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        assertEquals(1, surplusEvents.size());
        assertEquals("iron_ore", surplusEvents.get(0).commodityId);
        assertEquals(40, surplusEvents.get(0).excess);

        assertEquals(10, tickEvents.get(0).stationDeltas.get("s1").get("iron_ore"));
    }

    @Test
    void productionDistributedAcrossMultipleStations() {
        PlanetEconomyData planet = new PlanetEconomyData();
        planet.planetId = "p1";
        planet.population = 100000;
        planet.industryType = IndustryType.MINING;
        planet.childStationIds.add("s1");
        planet.childStationIds.add("s2");

        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 30;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("s1", new HashMap<>(Map.of("iron_ore", 50)));
        allStocks.put("s2", new HashMap<>(Map.of("iron_ore", 50)));
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("s1", new HashMap<>(Map.of("iron_ore", 100)));
        allMaxStocks.put("s2", new HashMap<>(Map.of("iron_ore", 200)));

        manager = new PlanetaryEconomyManager(eventBus, planetRegistry);
        manager.tick(allStocks, allMaxStocks);

        ProductionTickEvent evt = tickEvents.get(0);
        int s1Delta = evt.stationDeltas.get("s1").getOrDefault("iron_ore", 0);
        int s2Delta = evt.stationDeltas.get("s2").getOrDefault("iron_ore", 0);

        assertEquals(30, s1Delta + s2Delta);
        assertTrue(s2Delta > s1Delta, "Station with higher maxStock should get more production");
    }
}
