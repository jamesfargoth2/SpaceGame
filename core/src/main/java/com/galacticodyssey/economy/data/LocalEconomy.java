package com.galacticodyssey.economy.data;

import java.util.List;
import java.util.Map;

public final class LocalEconomy {
    public final long seed;
    public final Map<String, Float> priceModifiers;
    public final Map<String, SupplyLevel> supplyLevels;
    public final List<String> specializations;
    public final boolean blackMarketAvailable;
    public final float taxRate;

    public LocalEconomy(long seed, Map<String, Float> priceModifiers, Map<String, SupplyLevel> supplyLevels,
                        List<String> specializations, boolean blackMarketAvailable, float taxRate) {
        this.seed = seed;
        this.priceModifiers = Map.copyOf(priceModifiers);
        this.supplyLevels = Map.copyOf(supplyLevels);
        this.specializations = List.copyOf(specializations);
        this.blackMarketAvailable = blackMarketAvailable;
        this.taxRate = taxRate;
    }
}
