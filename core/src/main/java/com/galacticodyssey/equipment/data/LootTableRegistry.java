package com.galacticodyssey.equipment.data;

import java.util.HashMap;
import java.util.Map;

public class LootTableRegistry {
    private final Map<String, LootTable> tables = new HashMap<>();

    public void register(LootTable table) {
        tables.put(table.archetypeId, table);
    }

    public LootTable getTable(String archetypeId) {
        return tables.get(archetypeId);
    }

    public boolean hasTable(String archetypeId) {
        return tables.containsKey(archetypeId);
    }
}
