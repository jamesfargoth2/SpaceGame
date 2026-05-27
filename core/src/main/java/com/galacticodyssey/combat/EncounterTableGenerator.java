package com.galacticodyssey.combat;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class EncounterTableGenerator {

    public EncounterTable generate(long seed, RegionType region, int dangerLevel, String factionPresence) {
        long encounterSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.ENCOUNTER_DOMAIN), 0);
        Random rng = new Random(encounterSeed);

        Map<EncounterType, Float> weights = getBaseWeights(region);
        applyFactionModifier(weights, factionPresence);

        float dangerScale = 1f + (dangerLevel - 5) * 0.1f;
        weights.computeIfPresent(EncounterType.PIRATE_AMBUSH, (k, v) -> v * dangerScale);
        weights.computeIfPresent(EncounterType.ASTEROID_HAZARD, (k, v) -> v * dangerScale);

        for (EncounterType type : EncounterType.values()) {
            weights.computeIfPresent(type, (k, v) -> v * RngUtil.range(rng, 0.8f, 1.2f));
        }

        List<EncounterEntry> entries = new ArrayList<>();
        for (Map.Entry<EncounterType, Float> entry : weights.entrySet()) {
            if (entry.getValue() <= 0f) continue;
            int minDiff = Math.max(1, dangerLevel - 2);
            int maxDiff = Math.min(10, dangerLevel + 2);
            entries.add(new EncounterEntry(entry.getKey(), entry.getValue(), minDiff, maxDiff));
        }

        return new EncounterTable(encounterSeed, region, dangerLevel, entries);
    }

    private Map<EncounterType, Float> getBaseWeights(RegionType region) {
        Map<EncounterType, Float> weights = new EnumMap<>(EncounterType.class);
        switch (region) {
            case CORE_WORLD -> { weights.put(EncounterType.PATROL, 30f); weights.put(EncounterType.PIRATE_AMBUSH, 5f); weights.put(EncounterType.TRADER_CONVOY, 25f); weights.put(EncounterType.ASTEROID_HAZARD, 5f); weights.put(EncounterType.DERELICT_DISCOVERY, 5f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 15f); }
            case INNER_RIM -> { weights.put(EncounterType.PATROL, 20f); weights.put(EncounterType.PIRATE_AMBUSH, 10f); weights.put(EncounterType.TRADER_CONVOY, 20f); weights.put(EncounterType.ASTEROID_HAZARD, 10f); weights.put(EncounterType.DERELICT_DISCOVERY, 10f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 15f); }
            case OUTER_RIM -> { weights.put(EncounterType.PATROL, 10f); weights.put(EncounterType.PIRATE_AMBUSH, 20f); weights.put(EncounterType.TRADER_CONVOY, 15f); weights.put(EncounterType.ASTEROID_HAZARD, 15f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 10f); }
            case FRONTIER -> { weights.put(EncounterType.PATROL, 5f); weights.put(EncounterType.PIRATE_AMBUSH, 25f); weights.put(EncounterType.TRADER_CONVOY, 10f); weights.put(EncounterType.ASTEROID_HAZARD, 15f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 10f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 10f); }
            case LAWLESS -> { weights.put(EncounterType.PATROL, 2f); weights.put(EncounterType.PIRATE_AMBUSH, 35f); weights.put(EncounterType.TRADER_CONVOY, 5f); weights.put(EncounterType.ASTEROID_HAZARD, 10f); weights.put(EncounterType.DERELICT_DISCOVERY, 20f); weights.put(EncounterType.ANOMALY, 8f); weights.put(EncounterType.DISTRESS_SIGNAL, 15f); weights.put(EncounterType.NOTHING, 5f); }
            case NEBULA -> { weights.put(EncounterType.PATROL, 5f); weights.put(EncounterType.PIRATE_AMBUSH, 10f); weights.put(EncounterType.TRADER_CONVOY, 5f); weights.put(EncounterType.ASTEROID_HAZARD, 5f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 30f); weights.put(EncounterType.DISTRESS_SIGNAL, 15f); weights.put(EncounterType.NOTHING, 15f); }
            case ASTEROID_FIELD -> { weights.put(EncounterType.PATROL, 5f); weights.put(EncounterType.PIRATE_AMBUSH, 15f); weights.put(EncounterType.TRADER_CONVOY, 10f); weights.put(EncounterType.ASTEROID_HAZARD, 30f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 10f); }
        }
        return weights;
    }

    private void applyFactionModifier(Map<EncounterType, Float> weights, String factionPresence) {
        if (factionPresence == null) return;
        Map<EncounterType, Float> modifiers = getFactionModifiers(factionPresence);
        for (Map.Entry<EncounterType, Float> mod : modifiers.entrySet()) {
            weights.computeIfPresent(mod.getKey(), (k, v) -> v * mod.getValue());
        }
    }

    private Map<EncounterType, Float> getFactionModifiers(String presence) {
        Map<EncounterType, Float> mods = new EnumMap<>(EncounterType.class);
        switch (presence) {
            case "pirate_heavy" -> { mods.put(EncounterType.PIRATE_AMBUSH, 2.0f); mods.put(EncounterType.PATROL, 0.3f); mods.put(EncounterType.TRADER_CONVOY, 0.5f); }
            case "military_presence" -> { mods.put(EncounterType.PATROL, 2.5f); mods.put(EncounterType.PIRATE_AMBUSH, 0.4f); mods.put(EncounterType.DISTRESS_SIGNAL, 0.7f); }
            case "trade_hub" -> { mods.put(EncounterType.TRADER_CONVOY, 2.0f); mods.put(EncounterType.PIRATE_AMBUSH, 0.8f); mods.put(EncounterType.NOTHING, 0.5f); }
            case "abandoned" -> { mods.put(EncounterType.DERELICT_DISCOVERY, 2.5f); mods.put(EncounterType.PATROL, 0.1f); mods.put(EncounterType.TRADER_CONVOY, 0.2f); mods.put(EncounterType.ANOMALY, 1.5f); }
            case "contested" -> { mods.put(EncounterType.PIRATE_AMBUSH, 1.5f); mods.put(EncounterType.PATROL, 1.5f); mods.put(EncounterType.DISTRESS_SIGNAL, 1.8f); mods.put(EncounterType.NOTHING, 0.3f); }
        }
        return mods;
    }
}
