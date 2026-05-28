package com.galacticodyssey.crafting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillYieldBonusTest {

    private static final float YIELD_BONUS_PER_LEVEL = 0.005f;

    @Test
    void calculateYield_zeroSkill_returnsBase() {
        assertEquals(3, calculateYield(3, 0));
        assertEquals(5, calculateYield(5, 0));
    }

    @Test
    void calculateYield_level50_correctMultiplier() {
        // 1.0 + 50 * 0.005 = 1.25
        // floor(3 * 1.25) = floor(3.75) = 3
        assertEquals(3, calculateYield(3, 50));
        // floor(5 * 1.25) = floor(6.25) = 6
        assertEquals(6, calculateYield(5, 50));
    }

    @Test
    void calculateYield_level75_correctMultiplier() {
        // 1.0 + 75 * 0.005 = 1.375
        // floor(3 * 1.375) = floor(4.125) = 4
        assertEquals(4, calculateYield(3, 75));
        // floor(5 * 1.375) = floor(6.875) = 6
        assertEquals(6, calculateYield(5, 75));
    }

    @Test
    void calculateYield_level100_correctMultiplier() {
        // 1.0 + 100 * 0.005 = 1.5
        // floor(3 * 1.5) = floor(4.5) = 4
        assertEquals(4, calculateYield(3, 100));
        // floor(5 * 1.5) = floor(7.5) = 7
        assertEquals(7, calculateYield(5, 100));
    }

    @Test
    void calculateYield_alwaysAtLeastBase() {
        assertEquals(1, calculateYield(1, 0));
        assertEquals(1, calculateYield(1, 25));
    }

    @Test
    void refiningConfig_calculateYield_matchesFormula() {
        RefiningConfig config = new RefiningConfig(0.005f, "engineering");
        assertEquals(3, config.calculateYield(3, 0));
        assertEquals(6, config.calculateYield(5, 50));
        assertEquals(4, config.calculateYield(3, 75));
        assertEquals(7, config.calculateYield(5, 100));
    }

    @Test
    void refiningConfig_defaultConstructor_usesDefaultValues() {
        RefiningConfig config = new RefiningConfig();
        assertEquals(0.005f, config.yieldBonusPerLevel, 0.0001f);
        assertEquals("engineering", config.yieldSkillName);
    }

    private int calculateYield(int baseQuantity, int engineeringLevel) {
        float multiplier = 1.0f + engineeringLevel * YIELD_BONUS_PER_LEVEL;
        return Math.max(baseQuantity, (int) Math.floor(baseQuantity * multiplier));
    }
}
