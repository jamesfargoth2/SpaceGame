package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IntervalIteratingSystem;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.simulation.PricingFormula;

public class PricingSystem extends IntervalIteratingSystem {
    public static final int PRIORITY = 50;

    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);
    private static final ComponentMapper<PricingComponent> PRICING_M = ComponentMapper.getFor(PricingComponent.class);

    private final CommodityRegistry commodityRegistry;

    public PricingSystem(CommodityRegistry commodityRegistry, float interval) {
        super(Family.all(MarketComponent.class, PricingComponent.class).get(), interval, PRIORITY);
        this.commodityRegistry = commodityRegistry;
    }

    @Override
    protected void processEntity(Entity entity) {
        MarketComponent market = MARKET_M.get(entity);
        PricingComponent pricing = PRICING_M.get(entity);

        for (MarketEntry entry : market.entries.values()) {
            entry.stock = Math.min(entry.stock + (int) entry.supplyRate, entry.maxStock);

            CommodityDefinition commodity = commodityRegistry.get(entry.commodityId);
            if (commodity != null) {
                int price = PricingFormula.calculate(
                        commodity.basePrice, entry.stock, entry.demand, pricing.volatility);
                pricing.prices.put(entry.commodityId, price);
            }
        }
    }
}
