package com.galacticodyssey.economy.service;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
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
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.mission.job.ReputationQuery;

public class TransactionService {
    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);
    private static final ComponentMapper<PricingComponent> PRICING_M = ComponentMapper.getFor(PricingComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M = ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CargoBayComponent> CARGO_M = ComponentMapper.getFor(CargoBayComponent.class);

    private final CommodityRegistry commodityRegistry;
    private final EventBus eventBus;

    private float tradeReputationBonus;
    private ReputationQuery reputationQuery;

    public TransactionService(CommodityRegistry commodityRegistry, EventBus eventBus) {
        this.commodityRegistry = commodityRegistry;
        this.eventBus = eventBus;
    }

    public void setTradeReputationBonus(float bonus) {
        this.tradeReputationBonus = bonus;
    }

    public void setReputationQuery(ReputationQuery query) {
        this.reputationQuery = query;
    }

    public void buy(Entity station, Entity player, Entity ship, String commodityId, int quantity) {
        MarketComponent market = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo = CARGO_M.get(ship);
        CommodityDefinition commodity = commodityRegistry.get(commodityId);

        if (reputationQuery != null && market.ownerFactionId != null) {
            ReputationTier tier = ReputationTier.fromStanding(
                reputationQuery.getStanding(market.ownerFactionId));
            if (tier == ReputationTier.HOSTILE) {
                eventBus.publish(new TradeFailedEvent(
                    TradeFailureReason.HOSTILE_FACTION, commodityId, quantity));
                return;
            }
        }

        MarketEntry entry = market.entries.get(commodityId);
        int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);

        if (reputationQuery != null && market.ownerFactionId != null) {
            float mult = ReputationTier.fromStanding(
                reputationQuery.getStanding(market.ownerFactionId)).buyMultiplier;
            unitPrice = Math.round(unitPrice * mult);
        }

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
        if (market.ownerFactionId != null && tradeReputationBonus != 0f) {
            eventBus.publish(new ReputationChangeEvent(
                market.ownerFactionId, tradeReputationBonus,
                "trade:" + market.stationId));
        }
        eventBus.publish(new WalletChangedEvent(player.hashCode(), wallet.credits));
        eventBus.publish(new CargoChangedEvent(ship.hashCode()));
    }

    public void sell(Entity station, Entity player, Entity ship, String commodityId, int quantity) {
        MarketComponent market = MARKET_M.get(station);
        PricingComponent pricing = PRICING_M.get(station);
        PlayerWalletComponent wallet = WALLET_M.get(player);
        CargoBayComponent cargo = CARGO_M.get(ship);
        CommodityDefinition commodity = commodityRegistry.get(commodityId);

        if (reputationQuery != null && market.ownerFactionId != null) {
            ReputationTier tier = ReputationTier.fromStanding(
                reputationQuery.getStanding(market.ownerFactionId));
            if (tier == ReputationTier.HOSTILE) {
                eventBus.publish(new TradeFailedEvent(
                    TradeFailureReason.HOSTILE_FACTION, commodityId, quantity));
                return;
            }
        }

        int inCargo = cargo.contents.getOrDefault(commodityId, 0);
        if (inCargo < quantity) {
            eventBus.publish(new TradeFailedEvent(TradeFailureReason.COMMODITY_NOT_IN_CARGO, commodityId, quantity));
            return;
        }

        int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);

        if (reputationQuery != null && market.ownerFactionId != null) {
            float mult = ReputationTier.fromStanding(
                reputationQuery.getStanding(market.ownerFactionId)).sellMultiplier;
            unitPrice = Math.round(unitPrice * mult);
        }

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
        if (market.ownerFactionId != null && tradeReputationBonus != 0f) {
            eventBus.publish(new ReputationChangeEvent(
                market.ownerFactionId, tradeReputationBonus,
                "trade:" + market.stationId));
        }
        eventBus.publish(new WalletChangedEvent(player.hashCode(), wallet.credits));
        eventBus.publish(new CargoChangedEvent(ship.hashCode()));
    }
}
