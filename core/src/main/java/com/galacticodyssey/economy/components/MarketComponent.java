package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.economy.data.MarketEntry;

import java.util.HashMap;
import java.util.Map;

public class MarketComponent implements Component {
    public String stationId;
    public String ownerFactionId;
    public final Map<String, MarketEntry> entries = new HashMap<>();
}
