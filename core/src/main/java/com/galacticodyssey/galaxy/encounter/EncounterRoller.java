package com.galacticodyssey.galaxy.encounter;

import java.util.Random;

/**
 * Rolls encounters from a weighted {@link EncounterTable}.
 */
public final class EncounterRoller {

    private EncounterRoller() {}

    /**
     * Performs a weighted random selection from the encounter table.
     *
     * @param table the weighted encounter table
     * @param rng   the random number generator
     * @return the selected encounter type
     * @throws IllegalArgumentException if the table has no positive weights
     */
    public static EncounterType roll(EncounterTable table, Random rng) {
        // Iterate in enum ordinal order for deterministic results
        float total = 0f;
        for (EncounterType type : EncounterType.values()) {
            total += table.getWeight(type);
        }
        if (total <= 0f) {
            throw new IllegalArgumentException("Encounter table has no positive weights");
        }

        float pick = rng.nextFloat() * total;
        float cumulative = 0f;
        EncounterType last = null;
        for (EncounterType type : EncounterType.values()) {
            float w = table.getWeight(type);
            if (w <= 0f) continue;
            last = type;
            cumulative += w;
            if (pick < cumulative) return type;
        }
        // Fallback for floating-point edge case
        return last;
    }

    /**
     * Rolls an encounter with a base chance. Returns null if no encounter occurs.
     *
     * @param table      the weighted encounter table
     * @param baseChance probability (0-1) of an encounter occurring at all
     * @param rng        the random number generator
     * @return the selected encounter type, or null if no encounter occurs
     */
    public static EncounterType rollWithChance(EncounterTable table, float baseChance, Random rng) {
        if (rng.nextFloat() >= baseChance) return null;
        return roll(table, rng);
    }
}
