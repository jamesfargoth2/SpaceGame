package com.galacticodyssey.combat;

import java.util.List;
import java.util.Random;

public final class EncounterTable {
    public final long seed;
    public final RegionType regionType;
    public final int dangerLevel;
    public final List<EncounterEntry> entries;

    public EncounterTable(long seed, RegionType regionType, int dangerLevel, List<EncounterEntry> entries) {
        this.seed = seed;
        this.regionType = regionType;
        this.dangerLevel = dangerLevel;
        this.entries = List.copyOf(entries);
    }

    public EncounterEntry roll(Random rng) {
        float totalWeight = 0f;
        for (EncounterEntry e : entries) totalWeight += e.weight;
        float roll = rng.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (EncounterEntry e : entries) {
            cumulative += e.weight;
            if (roll <= cumulative) return e;
        }
        return entries.get(entries.size() - 1);
    }
}
