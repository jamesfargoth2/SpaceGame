package com.galacticodyssey.economy.procgen;

import java.util.Map;

/**
 * Utility for calculating trade route profitability between two systems.
 */
public final class TradeProfitCalculator {

    private TradeProfitCalculator() {}

    /**
     * Calculates the per-unit profit for a specific commodity when buying
     * at {@code buySystem} and selling at {@code sellSystem}.
     *
     * @param commodityId the commodity to trade
     * @param buySystem   the system where the commodity is purchased
     * @param sellSystem  the system where the commodity is sold
     * @return profit per unit (may be negative)
     */
    public static float routeProfit(String commodityId, SystemEconomy buySystem, SystemEconomy sellSystem) {
        Float buyPrice = buySystem.getBasePrice().get(commodityId);
        Float sellPrice = sellSystem.getBasePrice().get(commodityId);
        if (buyPrice == null || sellPrice == null) return 0f;
        return sellPrice - buyPrice;
    }

    /**
     * Finds the commodity with the highest trade profit between two systems,
     * excluding commodities that are contraband at the destination.
     *
     * @param buySystem  the system where commodities are purchased
     * @param sellSystem the system where commodities are sold
     * @return the commodity id with the highest profit, or null if no valid trade exists
     */
    public static String bestCommodity(SystemEconomy buySystem, SystemEconomy sellSystem) {
        String best = null;
        float bestProfit = Float.NEGATIVE_INFINITY;

        for (Map.Entry<String, Float> entry : buySystem.getBasePrice().entrySet()) {
            String id = entry.getKey();
            // Skip contraband at destination
            if (sellSystem.getContraband().contains(id)) continue;

            Float sellPrice = sellSystem.getBasePrice().get(id);
            if (sellPrice == null) continue;

            float profit = sellPrice - entry.getValue();
            if (profit > bestProfit) {
                bestProfit = profit;
                best = id;
            }
        }

        return best;
    }
}
