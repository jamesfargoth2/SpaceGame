package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityCategory;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.CommodityTier;
import com.galacticodyssey.economy.data.MarketEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingSystemTest {
    private Engine engine;
    private CommodityRegistry commodityRegistry;

    @BeforeEach
    void setUp() {
        commodityRegistry = new CommodityRegistry();

        CommodityDefinition iron = new CommodityDefinition();
        iron.id = "iron_ore";
        iron.name = "Iron Ore";
        iron.category = CommodityCategory.RAW_MATERIAL;
        iron.tier = CommodityTier.COMMON;
        iron.basePrice = 100;
        iron.mass = 2.0f;
        iron.volume = 1.5f;
        commodityRegistry.register(iron);

        engine = new Engine();
        PricingSystem system = new PricingSystem(commodityRegistry, 1.0f);
        engine.addSystem(system);
    }

    private Entity createStation(String stationId, int stock, int maxStock, float demand, float supplyRate) {
        Entity station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = stationId;
        market.entries.put("iron_ore", new MarketEntry("iron_ore", stock, maxStock, demand, supplyRate));
        station.add(market);

        PricingComponent pricing = new PricingComponent();
        pricing.volatility = 0f;
        station.add(pricing);

        engine.addEntity(station);
        return station;
    }

    @Test
    void recalculatesPricesOnTick() {
        Entity station = createStation("test_station", 50, 200, 50f, 0f);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertEquals(100, pricing.prices.get("iron_ore"));
    }

    @Test
    void lowStockIncreasesPrice() {
        Entity station = createStation("test_station", 10, 200, 50f, 0f);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertEquals(500, pricing.prices.get("iron_ore"));
    }

    @Test
    void highStockDecreasesPrice() {
        Entity station = createStation("test_station", 500, 500, 50f, 0f);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertEquals(20, pricing.prices.get("iron_ore"));
    }

    @Test
    void appliesNpcRestock() {
        Entity station = createStation("test_station", 50, 200, 50f, 10f);

        engine.update(1.0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(60, market.entries.get("iron_ore").stock);
    }

    @Test
    void restockCapsAtMaxStock() {
        Entity station = createStation("test_station", 195, 200, 50f, 10f);

        engine.update(1.0f);

        MarketComponent market = station.getComponent(MarketComponent.class);
        assertEquals(200, market.entries.get("iron_ore").stock);
    }
}
