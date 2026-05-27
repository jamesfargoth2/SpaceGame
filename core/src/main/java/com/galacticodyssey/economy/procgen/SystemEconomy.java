package com.galacticodyssey.economy.procgen;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Generated per-system economy data produced by {@link SystemEconomyGenerator}. */
public final class SystemEconomy {

    private final long systemId;
    private final EconomyType economyType;
    private final Map<String, Float> supply;
    private final Map<String, Float> demand;
    private final Map<String, Float> basePrice;
    private final Set<String> contraband;
    private final float wealthLevel;
    private final float techLevel;
    private final boolean hasBlackMarket;
    private final float volatility;

    public SystemEconomy(long systemId, EconomyType economyType,
                         Map<String, Float> supply, Map<String, Float> demand,
                         Map<String, Float> basePrice, Set<String> contraband,
                         float wealthLevel, float techLevel,
                         boolean hasBlackMarket, float volatility) {
        this.systemId = systemId;
        this.economyType = economyType;
        this.supply = Collections.unmodifiableMap(new HashMap<>(supply));
        this.demand = Collections.unmodifiableMap(new HashMap<>(demand));
        this.basePrice = Collections.unmodifiableMap(new HashMap<>(basePrice));
        this.contraband = Collections.unmodifiableSet(new HashSet<>(contraband));
        this.wealthLevel = wealthLevel;
        this.techLevel = techLevel;
        this.hasBlackMarket = hasBlackMarket;
        this.volatility = volatility;
    }

    public long getSystemId() { return systemId; }
    public EconomyType getEconomyType() { return economyType; }
    public Map<String, Float> getSupply() { return supply; }
    public Map<String, Float> getDemand() { return demand; }
    public Map<String, Float> getBasePrice() { return basePrice; }
    public Set<String> getContraband() { return contraband; }
    public float getWealthLevel() { return wealthLevel; }
    public float getTechLevel() { return techLevel; }
    public boolean hasBlackMarket() { return hasBlackMarket; }
    public float getVolatility() { return volatility; }
}
