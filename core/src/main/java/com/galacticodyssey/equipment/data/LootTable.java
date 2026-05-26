package com.galacticodyssey.equipment.data;

import java.util.List;

public class LootTable {
    public final String archetypeId;
    public final List<Entry> entries;
    public final float[] qualityWeights;

    public LootTable(String archetypeId, List<Entry> entries, float[] qualityWeights) {
        this.archetypeId = archetypeId;
        this.entries = entries;
        this.qualityWeights = qualityWeights;
    }

    public static class Entry {
        public final String itemId;
        public final String itemType;
        public final float dropChance;
        public final int minQuantity;
        public final int maxQuantity;

        public Entry(String itemId, String itemType, float dropChance,
                     int minQuantity, int maxQuantity) {
            this.itemId = itemId;
            this.itemType = itemType;
            this.dropChance = dropChance;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
        }
    }
}
