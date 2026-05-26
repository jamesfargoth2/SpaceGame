package com.galacticodyssey.economy.data;

import java.util.ArrayList;
import java.util.List;

public class PlanetEconomyData {
    public String planetId;
    public long population;
    public IndustryType industryType;
    public List<ProductionEntry> productions = new ArrayList<>();
    public List<ConsumptionEntry> consumptions = new ArrayList<>();
    public List<String> childStationIds = new ArrayList<>();

    public static class ProductionEntry {
        public String commodityId;
        public int outputPerTick;
    }

    public static class ConsumptionEntry {
        public String commodityId;
        public int demandPerTick;
    }
}
