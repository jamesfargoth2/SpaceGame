package com.galacticodyssey.galaxy.encounter;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EncounterRollerTest {

    @Test
    void deterministic() {
        EncounterContext ctx = new EncounterContext(
                1L, "faction_a", 0.5f, 0.5f, 0f, false, false);
        EncounterTable table = EncounterTableBuilder.build(ctx);

        long seed = 42L;
        EncounterType a = EncounterRoller.roll(table, new Random(seed));
        EncounterType b = EncounterRoller.roll(table, new Random(seed));

        assertEquals(a, b, "Same seed should produce the same encounter");
    }

    @Test
    void weightedDistribution() {
        // Create a simple table with known weights
        Map<EncounterType, Float> weights = new HashMap<>();
        weights.put(EncounterType.MERCHANT_CONVOY, 90f);
        weights.put(EncounterType.PIRATE_AMBUSH, 10f);
        EncounterTable table = new EncounterTable(weights);

        int merchantCount = 0;
        int pirateCount = 0;
        Random rng = new Random(12345L);
        int rolls = 10000;
        for (int i = 0; i < rolls; i++) {
            EncounterType result = EncounterRoller.roll(table, rng);
            if (result == EncounterType.MERCHANT_CONVOY) merchantCount++;
            else if (result == EncounterType.PIRATE_AMBUSH) pirateCount++;
        }

        // Merchant should appear roughly 90% of the time
        double merchantRatio = merchantCount / (double) rolls;
        assertTrue(merchantRatio > 0.85 && merchantRatio < 0.95,
                "MERCHANT_CONVOY should appear ~90% of rolls, got: " + merchantRatio);
        assertTrue(pirateCount > 0, "PIRATE_AMBUSH should appear at least once");
    }

    @Test
    void rollWithChanceReturnsNull() {
        Map<EncounterType, Float> weights = new HashMap<>();
        weights.put(EncounterType.MERCHANT_CONVOY, 10f);
        EncounterTable table = new EncounterTable(weights);

        // baseChance = 0 should always return null
        Random rng = new Random(42L);
        for (int i = 0; i < 100; i++) {
            EncounterType result = EncounterRoller.rollWithChance(table, 0f, rng);
            assertNull(result, "baseChance=0 should always return null");
        }
    }

    @Test
    void rollWithChanceReturnsEncounterWhenChanceIsOne() {
        Map<EncounterType, Float> weights = new HashMap<>();
        weights.put(EncounterType.MERCHANT_CONVOY, 10f);
        EncounterTable table = new EncounterTable(weights);

        // baseChance = 1 should always return an encounter
        Random rng = new Random(42L);
        for (int i = 0; i < 100; i++) {
            EncounterType result = EncounterRoller.rollWithChance(table, 1f, rng);
            assertNotNull(result, "baseChance=1 should always return an encounter");
        }
    }
}
