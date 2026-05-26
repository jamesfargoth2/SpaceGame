package com.galacticodyssey.economy.events;

import java.util.Map;

public final class ProductionTickEvent {
    public final String planetId;
    public final Map<String, Map<String, Integer>> stationDeltas;

    public ProductionTickEvent(String planetId, Map<String, Map<String, Integer>> stationDeltas) {
        this.planetId = planetId;
        this.stationDeltas = stationDeltas;
    }
}
