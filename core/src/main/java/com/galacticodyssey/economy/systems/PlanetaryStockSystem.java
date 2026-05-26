package com.galacticodyssey.economy.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.events.ProductionTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanetaryStockSystem extends EntitySystem {
    public static final int PRIORITY = 51;

    private static final ComponentMapper<MarketComponent> MARKET_M = ComponentMapper.getFor(MarketComponent.class);

    private final Map<String, Entity> stationIndex = new HashMap<>();
    private final List<ProductionTickEvent> pendingEvents = new ArrayList<>();
    private ImmutableArray<Entity> stations;

    public PlanetaryStockSystem(EventBus eventBus) {
        super(PRIORITY);
        eventBus.subscribe(ProductionTickEvent.class, pendingEvents::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        stations = engine.getEntitiesFor(Family.all(MarketComponent.class, PricingComponent.class).get());
        rebuildIndex();
    }

    @Override
    public void update(float deltaTime) {
        if (pendingEvents.isEmpty()) return;

        rebuildIndex();

        for (ProductionTickEvent event : pendingEvents) {
            for (Map.Entry<String, Map<String, Integer>> stationEntry : event.stationDeltas.entrySet()) {
                Entity station = stationIndex.get(stationEntry.getKey());
                if (station == null) continue;

                MarketComponent market = MARKET_M.get(station);
                for (Map.Entry<String, Integer> delta : stationEntry.getValue().entrySet()) {
                    MarketEntry entry = market.entries.get(delta.getKey());
                    if (entry != null) {
                        entry.stock = Math.max(0, entry.stock + delta.getValue());
                    }
                }
            }
        }
        pendingEvents.clear();
    }

    private void rebuildIndex() {
        stationIndex.clear();
        for (Entity entity : stations) {
            MarketComponent market = MARKET_M.get(entity);
            if (market.stationId != null) {
                stationIndex.put(market.stationId, entity);
            }
        }
    }

    public Map<String, Integer> getStationStocks(String stationId) {
        Entity station = stationIndex.get(stationId);
        if (station == null) return new HashMap<>();
        MarketComponent market = MARKET_M.get(station);
        Map<String, Integer> stocks = new HashMap<>();
        for (MarketEntry entry : market.entries.values()) {
            stocks.put(entry.commodityId, entry.stock);
        }
        return stocks;
    }

    public Map<String, Integer> getStationMaxStocks(String stationId) {
        Entity station = stationIndex.get(stationId);
        if (station == null) return new HashMap<>();
        MarketComponent market = MARKET_M.get(station);
        Map<String, Integer> maxStocks = new HashMap<>();
        for (MarketEntry entry : market.entries.values()) {
            maxStocks.put(entry.commodityId, entry.maxStock);
        }
        return maxStocks;
    }
}
