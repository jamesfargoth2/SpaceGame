package com.galacticodyssey.economy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.*;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.service.TransactionService;
import com.galacticodyssey.economy.simulation.PlanetaryEconomyManager;
import com.galacticodyssey.economy.systems.PlanetaryStockSystem;
import com.galacticodyssey.economy.systems.PricingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EconomyIntegrationTest {
    private Engine engine;
    private EventBus eventBus;
    private CommodityRegistry commodityRegistry;
    private TransactionService transactionService;
    private PlanetaryEconomyManager planetaryManager;
    private PlanetaryStockSystem planetaryStockSystem;

    private Entity station;
    private Entity player;
    private Entity ship;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
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

        CommodityDefinition food = new CommodityDefinition();
        food.id = "food_rations";
        food.name = "Food Rations";
        food.category = CommodityCategory.CONSUMABLE;
        food.tier = CommodityTier.COMMON;
        food.basePrice = 50;
        food.mass = 0.8f;
        food.volume = 0.8f;
        commodityRegistry.register(food);

        engine = new Engine();
        PricingSystem pricingSystem = new PricingSystem(commodityRegistry, 1.0f);
        planetaryStockSystem = new PlanetaryStockSystem(eventBus);
        engine.addSystem(pricingSystem);
        engine.addSystem(planetaryStockSystem);

        transactionService = new TransactionService(commodityRegistry, eventBus);

        station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = "test_station";
        market.entries.put("iron_ore", new MarketEntry("iron_ore", 100, 200, 50f, 0f));
        market.entries.put("food_rations", new MarketEntry("food_rations", 80, 150, 40f, 0f));
        station.add(market);
        PricingComponent pricing = new PricingComponent();
        pricing.volatility = 0f;
        station.add(pricing);
        engine.addEntity(station);

        player = new Entity();
        PlayerWalletComponent wallet = new PlayerWalletComponent();
        wallet.credits = 50000;
        player.add(wallet);

        ship = new Entity();
        CargoBayComponent cargo = new CargoBayComponent();
        cargo.capacity = 500f;
        ship.add(cargo);
    }

    @Test
    void buyingDepletsStockAndRaisesPrice() {
        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        int priceBefore = pricing.prices.get("iron_ore");

        transactionService.buy(station, player, ship, "iron_ore", 40);

        engine.update(1.0f);

        int priceAfter = pricing.prices.get("iron_ore");
        assertTrue(priceAfter > priceBefore,
                "Price should increase after buying 40 units (stock dropped from 100 to 60)");
    }

    @Test
    void sellingIncreasesStockAndLowersPrice() {
        CargoBayComponent cargo = ship.getComponent(CargoBayComponent.class);
        cargo.contents.put("iron_ore", 50);
        cargo.usedVolume = 75f;

        engine.update(1.0f);
        PricingComponent pricing = station.getComponent(PricingComponent.class);
        int priceBefore = pricing.prices.get("iron_ore");

        transactionService.sell(station, player, ship, "iron_ore", 50);

        engine.update(1.0f);
        int priceAfter = pricing.prices.get("iron_ore");
        assertTrue(priceAfter < priceBefore,
                "Price should decrease after selling 50 units (stock rose from 100 to 150)");
    }

    @Test
    void planetaryProductionIncreasesStationStock() {
        PlanetEconomyRegistry planetRegistry = new PlanetEconomyRegistry();
        PlanetEconomyData planet = new PlanetEconomyData();
        planet.planetId = "test_planet";
        planet.population = 100000;
        planet.industryType = IndustryType.MINING;
        planet.childStationIds.add("test_station");
        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 20;
        planet.productions.add(prod);
        planetRegistry.register(planet);

        planetaryManager = new PlanetaryEconomyManager(eventBus, planetRegistry);

        MarketComponent market = station.getComponent(MarketComponent.class);
        int stockBefore = market.entries.get("iron_ore").stock;

        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("test_station", Map.of("iron_ore", stockBefore));
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("test_station", Map.of("iron_ore", 200));

        planetaryManager.tick(allStocks, allMaxStocks);
        engine.update(0f);

        int stockAfter = market.entries.get("iron_ore").stock;
        assertEquals(stockBefore + 20, stockAfter);
    }

    @Test
    void fullCycleProduceTradeConsume() {
        PlanetEconomyRegistry planetRegistry = new PlanetEconomyRegistry();
        PlanetEconomyData planet = new PlanetEconomyData();
        planet.planetId = "test_planet";
        planet.population = 100000;
        planet.industryType = IndustryType.MINING;
        planet.childStationIds.add("test_station");

        PlanetEconomyData.ProductionEntry prod = new PlanetEconomyData.ProductionEntry();
        prod.commodityId = "iron_ore";
        prod.outputPerTick = 15;
        planet.productions.add(prod);

        PlanetEconomyData.ConsumptionEntry cons = new PlanetEconomyData.ConsumptionEntry();
        cons.commodityId = "food_rations";
        cons.demandPerTick = 10;
        planet.consumptions.add(cons);
        planetRegistry.register(planet);

        planetaryManager = new PlanetaryEconomyManager(eventBus, planetRegistry);

        MarketComponent market = station.getComponent(MarketComponent.class);
        int ironBefore = market.entries.get("iron_ore").stock;
        int foodBefore = market.entries.get("food_rations").stock;

        Map<String, Map<String, Integer>> allStocks = new HashMap<>();
        allStocks.put("test_station", Map.of("iron_ore", ironBefore, "food_rations", foodBefore));
        Map<String, Map<String, Integer>> allMaxStocks = new HashMap<>();
        allMaxStocks.put("test_station", Map.of("iron_ore", 200, "food_rations", 150));

        planetaryManager.tick(allStocks, allMaxStocks);
        engine.update(0f);

        assertEquals(ironBefore + 15, market.entries.get("iron_ore").stock);
        assertEquals(foodBefore - 10, market.entries.get("food_rations").stock);

        engine.update(1.0f);

        PricingComponent pricing = station.getComponent(PricingComponent.class);
        assertNotNull(pricing.prices.get("iron_ore"));
        assertNotNull(pricing.prices.get("food_rations"));

        List<TradeCompletedEvent> trades = new ArrayList<>();
        eventBus.subscribe(TradeCompletedEvent.class, trades::add);
        transactionService.buy(station, player, ship, "iron_ore", 10);
        assertEquals(1, trades.size());
        assertTrue(trades.get(0).isBuy);
    }

    @Test
    void twoStationsPriceDiverge() {
        CommodityDefinition food = commodityRegistry.get("food_rations");

        Entity station2 = new Entity();
        MarketComponent market2 = new MarketComponent();
        market2.stationId = "station_2";
        market2.entries.put("food_rations", new MarketEntry("food_rations", 10, 200, 80f, 0f));
        station2.add(market2);
        PricingComponent pricing2 = new PricingComponent();
        pricing2.volatility = 0f;
        station2.add(pricing2);
        engine.addEntity(station2);

        engine.update(1.0f);

        PricingComponent p1 = station.getComponent(PricingComponent.class);
        PricingComponent p2 = station2.getComponent(PricingComponent.class);

        int price1 = p1.prices.get("food_rations");
        int price2 = p2.prices.get("food_rations");

        assertTrue(price2 > price1,
                "Station with lower stock and higher demand should have higher price. " +
                "Station1=" + price1 + " Station2=" + price2);
    }
}
