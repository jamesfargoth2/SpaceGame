package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanetaryStockSystemTest {
    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        PlanetaryStockSystem system = new PlanetaryStockSystem(eventBus);
        engine.addSystem(system);
    }

    private Entity createStation(String stationId, String commodityId, int stock, int maxStock) {
        Entity station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = stationId;
        market.entries.put(commodityId, new MarketEntry(commodityId, stock, maxStock, 50f, 5f));
        station.add(market);

        PricingComponent pricing = new PricingComponent();
        station.add(pricing);

        engine.addEntity(station);
        return station;
    }

    @Test
    void appliesPositiveDeltaToStation() {
        Entity station = createStation("s1", "iron_ore", 50, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("iron_ore", 20)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(70, market.entries.get("iron_ore").stock);
    }

    @Test
    void appliesNegativeDeltaToStation() {
        Entity station = createStation("s1", "food_rations", 50, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("food_rations", -10)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(40, market.entries.get("food_rations").stock);
    }

    @Test
    void stockNeverGoesBelowZero() {
        Entity station = createStation("s1", "food_rations", 5, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("food_rations", -20)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(0, market.entries.get("food_rations").stock);
    }

    @Test
    void ignoresUnknownStationIds() {
        createStation("s1", "iron_ore", 50, 200);

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("unknown_station", new HashMap<>(Map.of("iron_ore", 20)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);
        // No exception thrown
    }

    @Test
    void handlesMultipleStationsAndCommodities() {
        Entity s1 = createStation("s1", "iron_ore", 50, 200);
        Entity s2 = createStation("s2", "iron_ore", 30, 200);

        s1.getComponent(MarketComponent.class).entries.put("food_rations",
                new MarketEntry("food_rations", 100, 200, 30f, 3f));

        Map<String, Map<String, Integer>> deltas = new HashMap<>();
        deltas.put("s1", new HashMap<>(Map.of("iron_ore", 10, "food_rations", -5)));
        deltas.put("s2", new HashMap<>(Map.of("iron_ore", 15)));
        eventBus.publish(new ProductionTickEvent("p1", deltas));

        engine.update(0f);

        assertEquals(60, s1.getComponent(MarketComponent.class).entries.get("iron_ore").stock);
        assertEquals(95, s1.getComponent(MarketComponent.class).entries.get("food_rations").stock);
        assertEquals(45, s2.getComponent(MarketComponent.class).entries.get("iron_ore").stock);
    }
}
