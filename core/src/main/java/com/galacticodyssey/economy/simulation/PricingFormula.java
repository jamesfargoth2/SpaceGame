package com.galacticodyssey.economy.simulation;

public final class PricingFormula {

    private static final float MIN_MULTIPLIER = 0.2f;
    private static final float MAX_MULTIPLIER = 5.0f;

    private PricingFormula() {}

    public static int calculate(int basePrice, int stock, float demand, float volatility) {
        float safeDivisor = Math.max(stock, 1);
        float rawMultiplier = demand / safeDivisor;
        float demandMultiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, rawMultiplier));
        float price = basePrice * demandMultiplier * (1f + volatility);
        return Math.max(1, Math.round(price));
    }

    public static float volatilityForStation(String stationId) {
        int hash = stationId.hashCode();
        float normalized = (hash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        return (normalized * 0.2f) - 0.1f;
    }
}
