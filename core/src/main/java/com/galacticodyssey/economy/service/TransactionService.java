package com.galacticodyssey.economy.service;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.CargoChangedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.economy.events.TradeFailedEvent;
import com.galacticodyssey.economy.events.WalletChangedEvent;

public class TransactionService {
    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);
    private static final ComponentMapper<PricingComponent> PRICING_M = ComponentMapper.getFor(PricingComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M = ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CargoBayComponent> CARGO_M = ComponentMapper.getFor(CargoBayComponent.class);

    private final CommodityRegistry commodityRegistry;
    private final EventBus eventBus;

    public TransactionService(CommodityRegistry commodityRegistry, EventBus eventBus) {
        this.commodityRegistry = commodityRegistry;
        this.eventBus = eventBus;
    }

    public void buy(Entity station, Entity player, Entity ship, String commodityId, int quantity) {
        MarketComponent market = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo = CARGO_M.get(ship);
        CommodityDefinition commodity = commodityRegistry.get(commodityId);

        MarketEntry entry = market.entries.get(commodityId);
        int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);
        int totalPrice = unitPrice * quantity;

        if (entry.stock < quantity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.INSUFFICIENT_STOCK, commodityId, quantity));
            return;
        }
        if (wallet.credits < totalPrice) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.INSUFFICIENT_FUNDS, commodityId, quantity));
            return;
        }
        float requiredVolume = commodity.volume * quantity;
        if (cargo.usedVolume + requiredVolume > cargo.capacity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.CARGO_FULL, commodityId, quantity));
            return;
        }

        wallet.credits -= totalPrice;
        entry.stock -= quantity;
        cargo.contents.merge(commodityId, quantity, Integer::sum);
        cargo.usedVolume += requiredVolume;

        eventBus.publish(new TradeCompletedEvent(market.stationId, commodityId, quantity, unitPrice, totalPrice, true));
        eventBus.publish(new WalletChangedEvent(player.hashCode(), wallet.credits));
        eventBus.publish(new CargoChangedEvent(ship.hashCode()));
    }

    public void sell(Entity station, Entity player, Entity ship, String commodityId, int quantity) {
        MarketComponent market = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo = CARGO_M.get(ship);
        CommodityDefinition commodity = commodityRegistry.get(commodityId);

        int inCargo = cargo.contents.getOrDefault(commodityId, 0);
        if (inCargo < quantity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.COMMODITY_NOT_IN_CARGO, commodityId, quantity));
            return;
        }

        int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);
        int totalPrice = unitPrice * quantity;

        MarketEntry entry = market.entries.get(commodityId);
        entry.stock += quantity;

        int remaining = inCargo - quantity;
        if (remaining <= 0) {
            cargo.contents.remove(commodityId);
        } else {
            cargo.contents.put(commodityId, remaining);
        }
        cargo.usedVolume -= commodity.volume * quantity;

        wallet.credits += totalPrice;

        eventBus.publish(new TradeCompletedEvent(market.stationId, commodityId, quantity, unitPrice, totalPrice, false));
        eventBus.publish(new WalletChangedEvent(player.hashCode(), wallet.credits));
        eventBus.publish(new CargoChangedEvent(ship.hashCode()));
    }
}
