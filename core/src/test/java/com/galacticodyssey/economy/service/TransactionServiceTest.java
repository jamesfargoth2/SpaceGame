package com.galacticodyssey.economy.service;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityCategory;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.CommodityTier;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.CargoChangedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.events.TradeFailedEvent;
import com.galacticodyssey.economy.events.WalletChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    private EventBus eventBus;
    private CommodityRegistry commodityRegistry;
    private TransactionService service;

    private Entity station;
    private Entity player;
    private Entity ship;

    private MarketComponent market;
    private PricingComponent pricing;
    private PlayerWalletComponent wallet;
    private CargoBayComponent cargo;

    private final List<TradeCompletedEvent> completedEvents = new ArrayList<>();
    private final List<TradeFailedEvent> failedEvents = new ArrayList<>();
    private final List<WalletChangedEvent> walletEvents = new ArrayList<>();
    private final List<CargoChangedEvent> cargoEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        commodityRegistry = new CommodityRegistry();

        CommodityDefinition iron = new CommodityDefinition();
        iron.id = "iron_ore";
        iron.name = "Iron Ore";
        iron.category = CommodityCategory.RAW_MATERIAL;
        iron.tier = CommodityTier.COMMON;
        iron.basePrice = 15;
        iron.mass = 2.0f;
        iron.volume = 1.5f;
        commodityRegistry.register(iron);

        service = new TransactionService(commodityRegistry, eventBus);

        station = new Entity();
        market = new MarketComponent();
        market.stationId = "station_1";
        market.entries.put("iron_ore", new MarketEntry("iron_ore", 100, 200, 50f, 5f));
        station.add(market);
        pricing = new PricingComponent();
        pricing.prices.put("iron_ore", 15);
        station.add(pricing);

        player = new Entity();
        wallet = new PlayerWalletComponent();
        wallet.credits = 1000;
        player.add(wallet);

        ship = new Entity();
        cargo = new CargoBayComponent();
        cargo.capacity = 100f;
        cargo.usedVolume = 0f;
        ship.add(cargo);

        eventBus.subscribe(TradeCompletedEvent.class, completedEvents::add);
        eventBus.subscribe(TradeFailedEvent.class, failedEvents::add);
        eventBus.subscribe(WalletChangedEvent.class, walletEvents::add);
        eventBus.subscribe(CargoChangedEvent.class, cargoEvents::add);
    }

    @Test
    void buyDeductsCreditsAndTransfersStock() {
        service.buy(station, player, ship, "iron_ore", 10);

        assertEquals(850, wallet.credits);
        assertEquals(90, market.entries.get("iron_ore").stock);
        assertEquals(10, cargo.contents.getOrDefault("iron_ore", 0));
        assertEquals(15.0f, cargo.usedVolume, 0.01f);
    }

    @Test
    void buyPublishesEvents() {
        service.buy(station, player, ship, "iron_ore", 5);

        assertEquals(1, completedEvents.size());
        TradeCompletedEvent evt = completedEvents.get(0);
        assertEquals("station_1", evt.stationId);
        assertEquals("iron_ore", evt.commodityId);
        assertEquals(5, evt.quantity);
        assertEquals(15, evt.unitPrice);
        assertEquals(75, evt.totalPrice);
        assertTrue(evt.isBuy);

        assertEquals(1, walletEvents.size());
        assertEquals(925, walletEvents.get(0).newBalance);

        assertEquals(1, cargoEvents.size());
    }

    @Test
    void buyFailsWithInsufficientFunds() {
        wallet.credits = 10;

        service.buy(station, player, ship, "iron_ore", 10);

        assertEquals(10, wallet.credits);
        assertEquals(100, market.entries.get("iron_ore").stock);
        assertEquals(0, completedEvents.size());
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.INSUFFICIENT_FUNDS, failedEvents.get(0).reason);
    }

    @Test
    void buyFailsWithInsufficientStock() {
        service.buy(station, player, ship, "iron_ore", 200);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.INSUFFICIENT_STOCK, failedEvents.get(0).reason);
    }

    @Test
    void buyFailsWithCargoFull() {
        cargo.capacity = 1.0f;

        service.buy(station, player, ship, "iron_ore", 10);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.CARGO_FULL, failedEvents.get(0).reason);
    }

    @Test
    void sellAddsCreditsAndTransfersStock() {
        cargo.contents.put("iron_ore", 20);
        cargo.usedVolume = 30.0f;

        service.sell(station, player, ship, "iron_ore", 10);

        assertEquals(1150, wallet.credits);
        assertEquals(110, market.entries.get("iron_ore").stock);
        assertEquals(10, cargo.contents.get("iron_ore"));
        assertEquals(15.0f, cargo.usedVolume, 0.01f);
    }

    @Test
    void sellPublishesEvents() {
        cargo.contents.put("iron_ore", 5);
        cargo.usedVolume = 7.5f;

        service.sell(station, player, ship, "iron_ore", 5);

        assertEquals(1, completedEvents.size());
        assertFalse(completedEvents.get(0).isBuy);
        assertEquals(75, completedEvents.get(0).totalPrice);

        assertEquals(1, walletEvents.size());
        assertEquals(1, cargoEvents.size());
    }

    @Test
    void sellFailsWhenCommodityNotInCargo() {
        service.sell(station, player, ship, "iron_ore", 5);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.COMMODITY_NOT_IN_CARGO, failedEvents.get(0).reason);
    }

    @Test
    void sellFailsWhenNotEnoughInCargo() {
        cargo.contents.put("iron_ore", 3);
        cargo.usedVolume = 4.5f;

        service.sell(station, player, ship, "iron_ore", 10);

        assertEquals(1000, wallet.credits);
        assertEquals(1, failedEvents.size());
        assertEquals(TradeFailureReason.COMMODITY_NOT_IN_CARGO, failedEvents.get(0).reason);
    }

    @Test
    void sellRemovesCommodityEntryWhenFullyDepleted() {
        cargo.contents.put("iron_ore", 5);
        cargo.usedVolume = 7.5f;

        service.sell(station, player, ship, "iron_ore", 5);

        assertFalse(cargo.contents.containsKey("iron_ore"));
        assertEquals(0f, cargo.usedVolume, 0.01f);
    }
}
