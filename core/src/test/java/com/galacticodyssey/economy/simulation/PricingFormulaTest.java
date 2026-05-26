package com.galacticodyssey.economy.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingFormulaTest {

    @Test
    void normalSupplyDemandBalance() {
        int price = PricingFormula.calculate(100, 50, 50f, 0f);
        assertEquals(100, price);
    }

    @Test
    void lowStockDrivesUpPrice() {
        int price = PricingFormula.calculate(100, 10, 50f, 0f);
        assertEquals(500, price);
    }

    @Test
    void highStockDrivesDownPrice() {
        int price = PricingFormula.calculate(100, 500, 50f, 0f);
        assertEquals(20, price);
    }

    @Test
    void zeroStockUsesMaxMultiplier() {
        int price = PricingFormula.calculate(100, 0, 50f, 0f);
        assertEquals(500, price);
    }

    @Test
    void zeroDemandUsesMinMultiplier() {
        int price = PricingFormula.calculate(100, 50, 0f, 0f);
        assertEquals(20, price);
    }

    @Test
    void positiveVolatilityIncreasesPrice() {
        int price = PricingFormula.calculate(100, 50, 50f, 0.1f);
        assertEquals(110, price);
    }

    @Test
    void negativeVolatilityDecreasesPrice() {
        int price = PricingFormula.calculate(100, 50, 50f, -0.1f);
        assertEquals(90, price);
    }

    @Test
    void priceNeverBelowOne() {
        int price = PricingFormula.calculate(1, 1000, 1f, -0.1f);
        assertTrue(price >= 1, "Price should never drop below 1 credit");
    }

    @Test
    void demandMultiplierClampedAt5() {
        int price = PricingFormula.calculate(100, 1, 1000f, 0f);
        assertEquals(500, price);
    }

    @Test
    void demandMultiplierClampedAtPointTwo() {
        int price = PricingFormula.calculate(100, 10000, 1f, 0f);
        assertEquals(20, price);
    }

    @Test
    void volatilityFromSeed() {
        float v1 = PricingFormula.volatilityForStation("station_alpha");
        float v2 = PricingFormula.volatilityForStation("station_beta");
        float v1Again = PricingFormula.volatilityForStation("station_alpha");

        assertEquals(v1, v1Again, "Same station ID should produce same volatility");
        assertTrue(v1 >= -0.1f && v1 <= 0.1f, "Volatility should be in [-0.1, 0.1]");
        assertTrue(v2 >= -0.1f && v2 <= 0.1f, "Volatility should be in [-0.1, 0.1]");
    }
}
