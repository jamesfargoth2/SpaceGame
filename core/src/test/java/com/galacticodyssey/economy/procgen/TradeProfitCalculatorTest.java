package com.galacticodyssey.economy.procgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeProfitCalculatorTest {

    @Test
    void profitPositiveWhenBuyLowSellHigh() {
        // Mining colony: high iron_ore supply → low price
        // Industrial: high iron_ore demand → high price
        SystemEconomy mining = findEconomyOfType(EconomyType.MINING_COLONY, null, 0.5f);
        SystemEconomy industrial = findEconomyOfType(EconomyType.INDUSTRIAL, null, 0.5f);
        assertNotNull(mining);
        assertNotNull(industrial);

        float profit = TradeProfitCalculator.routeProfit("iron_ore", mining, industrial);
        assertTrue(profit > 0,
                "Buying iron_ore at MINING_COLONY and selling at INDUSTRIAL should yield profit, got: " + profit);
    }

    @Test
    void bestCommodityExcludesContraband() {
        // MILITARIST destination has salvaged_components as contraband
        SystemEconomy pirate = findEconomyOfType(EconomyType.PIRATE_OUTPOST, "PIRATE_SYNDICATE", 0.5f);
        SystemEconomy militarist = SystemEconomyGenerator.generate(42L, "MILITARIST", 0.3f);
        assertNotNull(pirate);

        String best = TradeProfitCalculator.bestCommodity(pirate, militarist);
        assertNotNull(best, "Should find at least one tradeable commodity");
        assertFalse(militarist.getContraband().contains(best),
                "Best commodity should not be contraband at destination, got: " + best);
    }

    @Test
    void zeroProfitWhenSameEconomy() {
        SystemEconomy economy = SystemEconomyGenerator.generate(777L, "FEDERATION", 0.4f);

        // Buying and selling at the same economy yields zero profit
        float profit = TradeProfitCalculator.routeProfit("iron_ore", economy, economy);
        assertEquals(0f, profit, 0.001f,
                "Trading within the same economy should yield zero profit");
    }

    /** Searches up to 1000 seeds to find an economy of the given type. */
    private SystemEconomy findEconomyOfType(EconomyType type, String ethos, float dist) {
        for (long seed = 1; seed <= 1000; seed++) {
            SystemEconomy e = SystemEconomyGenerator.generate(seed, ethos, dist);
            if (e.getEconomyType() == type) return e;
        }
        return null;
    }
}
