package com.galacticodyssey.galaxy.encounter;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a weighted {@link EncounterTable} from an {@link EncounterContext},
 * applying security, rim, war, contested, and reputation modifiers to base weights.
 */
public final class EncounterTableBuilder {

    private EncounterTableBuilder() {}

    /**
     * Builds a fully modified encounter table for the given context.
     *
     * @param ctx the encounter context describing system conditions
     * @return a weighted encounter table
     */
    public static EncounterTable build(EncounterContext ctx) {
        Map<EncounterType, Float> weights = new HashMap<>();

        // Base weights
        weights.put(EncounterType.MERCHANT_CONVOY, 8f);
        weights.put(EncounterType.FACTION_PATROL, 6f);
        weights.put(EncounterType.PIRATE_PATROL, 4f);
        weights.put(EncounterType.PIRATE_AMBUSH, 2f);
        weights.put(EncounterType.DISTRESS_BEACON, 3f);
        weights.put(EncounterType.DERELICT_DISCOVERY, 2f);
        weights.put(EncounterType.DEBRIS_FIELD, 1f);
        weights.put(EncounterType.SCIENCE_VESSEL, 2f);
        weights.put(EncounterType.SALVAGE_CREW, 1f);
        weights.put(EncounterType.MYSTERY_SIGNAL, 0.5f);
        weights.put(EncounterType.FACTIONAL_SKIRMISH, 0.5f);

        // All others start at 0
        weights.put(EncounterType.BOUNTY_HUNTER, 0f);
        weights.put(EncounterType.HOSTILE_FLEET, 0f);
        weights.put(EncounterType.MERCENARY_GROUP, 0f);
        weights.put(EncounterType.REFUGEE_FLEET, 0f);
        weights.put(EncounterType.ASTEROID_SWARM, 0f);
        weights.put(EncounterType.ANOMALY_ENCOUNTER, 0f);
        weights.put(EncounterType.BOUNTY_TARGET_SIGHTED, 0f);
        weights.put(EncounterType.WANTED_CRIMINAL, 0f);

        applySecurityModifiers(weights, ctx.getSecurityLevel());
        applyRimModifiers(weights, ctx.getDistanceFromCoreLY());
        applyWarModifiers(weights, ctx.isAtWar());
        applyContestedModifiers(weights, ctx.isContested());
        applyReputationModifiers(weights, ctx.getPlayerReputation());

        // Clamp all weights to non-negative
        for (Map.Entry<EncounterType, Float> entry : weights.entrySet()) {
            if (entry.getValue() < 0f) {
                entry.setValue(0f);
            }
        }

        return new EncounterTable(weights);
    }

    private static void applySecurityModifiers(Map<EncounterType, Float> weights, float sec) {
        multiply(weights, EncounterType.FACTION_PATROL, 1f + sec * 2f);
        multiply(weights, EncounterType.PIRATE_PATROL, 1f - sec * 0.8f);
        multiply(weights, EncounterType.PIRATE_AMBUSH, 1f - sec * 0.9f);
        multiply(weights, EncounterType.MERCHANT_CONVOY, 1f + sec);
    }

    private static void applyRimModifiers(Map<EncounterType, Float> weights, float rim) {
        multiply(weights, EncounterType.PIRATE_AMBUSH, 1f + rim * 3f);
        multiply(weights, EncounterType.DERELICT_DISCOVERY, 1f + rim * 2f);
        multiply(weights, EncounterType.MYSTERY_SIGNAL, 1f + rim * 2f);
        multiply(weights, EncounterType.MERCHANT_CONVOY, Math.max(0.1f, 1f - rim));
    }

    private static void applyWarModifiers(Map<EncounterType, Float> weights, boolean isAtWar) {
        if (!isAtWar) return;
        weights.put(EncounterType.HOSTILE_FLEET, 5f);
        weights.put(EncounterType.FACTIONAL_SKIRMISH, 4f);
        weights.put(EncounterType.DEBRIS_FIELD, 3f);
        weights.put(EncounterType.REFUGEE_FLEET, 2f);
        multiply(weights, EncounterType.MERCHANT_CONVOY, 0.3f);
    }

    private static void applyContestedModifiers(Map<EncounterType, Float> weights, boolean isContested) {
        if (!isContested) return;
        // Boost all combat-type encounters by 1.5x
        multiply(weights, EncounterType.PIRATE_AMBUSH, 1.5f);
        multiply(weights, EncounterType.PIRATE_PATROL, 1.5f);
        multiply(weights, EncounterType.FACTION_PATROL, 1.5f);
        multiply(weights, EncounterType.BOUNTY_HUNTER, 1.5f);
        multiply(weights, EncounterType.HOSTILE_FLEET, 1.5f);
        multiply(weights, EncounterType.MERCENARY_GROUP, 1.5f);
        multiply(weights, EncounterType.FACTIONAL_SKIRMISH, 1.5f);
    }

    private static void applyReputationModifiers(Map<EncounterType, Float> weights, float rep) {
        if (rep > 0.5f) {
            weights.put(EncounterType.BOUNTY_HUNTER, 0f);
        } else if (rep < -0.5f) {
            weights.put(EncounterType.BOUNTY_HUNTER, 3f);
            multiply(weights, EncounterType.FACTION_PATROL, 0.5f);
        }
    }

    private static void multiply(Map<EncounterType, Float> weights, EncounterType type, float factor) {
        weights.put(type, weights.getOrDefault(type, 0f) * factor);
    }
}
