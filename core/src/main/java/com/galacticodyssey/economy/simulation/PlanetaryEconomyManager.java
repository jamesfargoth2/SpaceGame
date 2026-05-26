package com.galacticodyssey.economy.simulation;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.economy.data.PlanetEconomyData;
import com.galacticodyssey.economy.data.PlanetEconomyRegistry;
import com.galacticodyssey.economy.events.ProductionTickEvent;
import com.galacticodyssey.economy.events.ShortageEvent;
import com.galacticodyssey.economy.events.SurplusEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanetaryEconomyManager {
    private final EventBus eventBus;
    private final PlanetEconomyRegistry planetRegistry;

    public PlanetaryEconomyManager(EventBus eventBus, PlanetEconomyRegistry planetRegistry) {
        this.eventBus = eventBus;
        this.planetRegistry = planetRegistry;
    }

    public void tick(Map<String, Map<String, Integer>> stationStocks,
                     Map<String, Map<String, Integer>> stationMaxStocks) {
        for (PlanetEconomyData planet : planetRegistry.getAll()) {
            Map<String, Map<String, Integer>> stationDeltas = new HashMap<>();
            for (String stationId : planet.childStationIds) {
                stationDeltas.put(stationId, new HashMap<>());
            }

            distributeProduction(planet, stationDeltas, stationStocks, stationMaxStocks);
            distributeConsumption(planet, stationDeltas, stationStocks);

            eventBus.publish(new ProductionTickEvent(planet.planetId, stationDeltas));
        }
    }

    private void distributeProduction(PlanetEconomyData planet,
                                       Map<String, Map<String, Integer>> stationDeltas,
                                       Map<String, Map<String, Integer>> stationStocks,
                                       Map<String, Map<String, Integer>> stationMaxStocks) {
        List<String> stations = planet.childStationIds;
        if (stations.isEmpty()) return;

        for (PlanetEconomyData.ProductionEntry prod : planet.productions) {
            int totalMaxStock = 0;
            for (String stationId : stations) {
                Map<String, Integer> maxStocks = stationMaxStocks.get(stationId);
                if (maxStocks != null) {
                    totalMaxStock += maxStocks.getOrDefault(prod.commodityId, 100);
                }
            }
            if (totalMaxStock == 0) totalMaxStock = 1;

            int totalSurplus = 0;

            for (String stationId : stations) {
                Map<String, Integer> maxStocks = stationMaxStocks.get(stationId);
                int stationMax = (maxStocks != null) ? maxStocks.getOrDefault(prod.commodityId, 100) : 100;
                float weight = (float) stationMax / totalMaxStock;
                int share = Math.round(prod.outputPerTick * weight);

                Map<String, Integer> stocks = stationStocks.get(stationId);
                int currentStock = (stocks != null) ? stocks.getOrDefault(prod.commodityId, 0) : 0;
                int available = stationMax - currentStock;
                int actualAdd = Math.min(share, available);
                int overflow = share - actualAdd;

                stationDeltas.get(stationId).merge(prod.commodityId, actualAdd, Integer::sum);
                totalSurplus += overflow;
            }

            if (totalSurplus > 0) {
                eventBus.publish(new SurplusEvent(planet.planetId, prod.commodityId, totalSurplus));
            }
        }
    }

    private void distributeConsumption(PlanetEconomyData planet,
                                        Map<String, Map<String, Integer>> stationDeltas,
                                        Map<String, Map<String, Integer>> stationStocks) {
        List<String> stations = planet.childStationIds;
        if (stations.isEmpty()) return;

        for (PlanetEconomyData.ConsumptionEntry cons : planet.consumptions) {
            int demandPerStation = cons.demandPerTick / stations.size();
            int remainder = cons.demandPerTick % stations.size();
            int totalDeficit = 0;

            for (int i = 0; i < stations.size(); i++) {
                String stationId = stations.get(i);
                int stationDemand = demandPerStation + (i < remainder ? 1 : 0);

                Map<String, Integer> stocks = stationStocks.get(stationId);
                int currentStock = (stocks != null) ? stocks.getOrDefault(cons.commodityId, 0) : 0;
                int pendingDelta = stationDeltas.get(stationId).getOrDefault(cons.commodityId, 0);
                int effectiveStock = currentStock + pendingDelta;

                int actualRemove = Math.min(stationDemand, Math.max(effectiveStock, 0));
                int deficit = stationDemand - actualRemove;

                stationDeltas.get(stationId).merge(cons.commodityId, -actualRemove, Integer::sum);
                totalDeficit += deficit;
            }

            if (totalDeficit > 0) {
                eventBus.publish(new ShortageEvent(planet.planetId, cons.commodityId, totalDeficit));
            }
        }
    }
}
