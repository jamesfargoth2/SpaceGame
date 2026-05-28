package com.galacticodyssey.economy;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityCategory;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.CommodityTier;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.TradeFailedEvent;
import com.galacticodyssey.economy.service.TradeFailureReason;
import com.galacticodyssey.economy.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceReputationTest {

    private EventBus eventBus;
    private TransactionService service;
    private Entity station;
    private Entity player;
    private Entity ship;
    private MarketComponent market;
    private PlayerWalletComponent wallet;
    private CargoBayComponent cargo;
    private PricingComponent pricing;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();

        CommodityRegistry registry = new CommodityRegistry();
        CommodityDefinition fuel = new CommodityDefinition();
        fuel.id = "fuel";
        fuel.name = "Fuel";
        fuel.category = CommodityCategory.RAW_MATERIAL;
        fuel.tier = CommodityTier.COMMON;
        fuel.basePrice = 10;
        fuel.mass = 1.0f;
        fuel.volume = 1.0f;
        registry.register(fuel);

        service = new TransactionService(registry, eventBus);
        service.setTradeReputationBonus(1.0f);

        // Station entity
        station = new Entity();
        market = new MarketComponent();
        market.stationId = "station_alpha";
        market.ownerFactionId = "fed";
        market.entries.put("fuel", new MarketEntry("fuel", 100, 200, 50f, 0f));
        pricing = new PricingComponent();
        pricing.prices.put("fuel", 10);
        station.add(market);
        station.add(pricing);

        // Player entity
        player = new Entity();
        wallet = new PlayerWalletComponent();
        wallet.credits = 1000L;
        player.add(wallet);

        // Ship entity
        ship = new Entity();
        cargo = new CargoBayComponent();
        cargo.capacity = 100f;
        ship.add(cargo);
    }

    @Test
    void buyAtFactionStationPublishesReputationEvent() {
        List<ReputationChangeEvent> repEvents = new ArrayList<>();
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);

        service.buy(station, player, ship, "fuel", 1);

        assertEquals(1, repEvents.size());
        assertEquals("fed", repEvents.get(0).factionId);
        assertEquals(1.0f, repEvents.get(0).delta, 0.001f);
        assertTrue(repEvents.get(0).sourceId.contains("station_alpha"));
    }

    @Test
    void sellAtFactionStationPublishesReputationEvent() {
        cargo.contents.put("fuel", 5);
        cargo.usedVolume = 5f;

        List<ReputationChangeEvent> repEvents = new ArrayList<>();
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);

        service.sell(station, player, ship, "fuel", 5);

        assertEquals(1, repEvents.size());
        assertEquals("fed", repEvents.get(0).factionId);
        assertEquals(1.0f, repEvents.get(0).delta, 0.001f);
    }

    @Test
    void buyAtNonFactionStationNoReputationEvent() {
        market.ownerFactionId = null;

        List<ReputationChangeEvent> repEvents = new ArrayList<>();
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);

        service.buy(station, player, ship, "fuel", 1);

        assertEquals(0, repEvents.size());
    }

    @Test
    void buyAtFriendlyStationAppliesDiscount() {
        // Use price 100 so 100 * 0.95 = 95 is clearly measurable
        pricing.prices.put("fuel", 100);
        wallet.credits = 10000L;

        // FRIENDLY tier: standing >= 25f, buyMultiplier = 0.95
        service.setReputationQuery(factionId -> 30f);

        service.buy(station, player, ship, "fuel", 1);

        // Expected price: Math.round(100 * 0.95f) = Math.round(95.0f) = 95
        long expectedCredits = 10000L - 95L;
        assertEquals(expectedCredits, wallet.credits);
    }

    @Test
    void buyAtHostileStationDenied() {
        long creditsBefore = wallet.credits;

        // HOSTILE tier: standing < -50f
        service.setReputationQuery(factionId -> -60f);

        List<TradeFailedEvent> failEvents = new ArrayList<>();
        eventBus.subscribe(TradeFailedEvent.class, failEvents::add);

        service.buy(station, player, ship, "fuel", 1);

        // Credits unchanged
        assertEquals(creditsBefore, wallet.credits);
        // TradeFailedEvent with HOSTILE_FACTION reason
        assertEquals(1, failEvents.size());
        assertEquals(TradeFailureReason.HOSTILE_FACTION, failEvents.get(0).reason);
    }

    @Test
    void sellAtHostileStationDenied() {
        cargo.contents.put("fuel", 5);
        cargo.usedVolume = 5f;
        long creditsBefore = wallet.credits;

        service.setReputationQuery(factionId -> -60f);

        List<TradeFailedEvent> failEvents = new ArrayList<>();
        eventBus.subscribe(TradeFailedEvent.class, failEvents::add);

        service.sell(station, player, ship, "fuel", 5);

        // Cargo unchanged
        assertEquals(5, cargo.contents.get("fuel"));
        assertEquals(creditsBefore, wallet.credits);
        assertEquals(1, failEvents.size());
        assertEquals(TradeFailureReason.HOSTILE_FACTION, failEvents.get(0).reason);
    }

    @Test
    void buyAtNeutralStationNoPriceChange() {
        long creditsBefore = wallet.credits;

        // NEUTRAL tier: standing >= 0f, buyMultiplier = 1.0
        service.setReputationQuery(factionId -> 5f);

        service.buy(station, player, ship, "fuel", 1);

        // Price stays at 10 (1.0 multiplier)
        assertEquals(creditsBefore - 10L, wallet.credits);
    }
}
