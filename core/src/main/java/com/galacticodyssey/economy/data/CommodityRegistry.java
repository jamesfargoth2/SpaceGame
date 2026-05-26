package com.galacticodyssey.economy.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommodityRegistry {
    private final Map<String, CommodityDefinition> byId = new HashMap<>();

    public void register(CommodityDefinition definition) {
        byId.put(definition.id, definition);
    }

    public CommodityDefinition get(String id) {
        return byId.get(id);
    }

    public List<CommodityDefinition> getByCategory(CommodityCategory category) {
        List<CommodityDefinition> result = new ArrayList<>();
        for (CommodityDefinition def : byId.values()) {
            if (def.category == category) {
                result.add(def);
            }
        }
        return result;
    }

    public List<CommodityDefinition> getByTier(CommodityTier tier) {
        List<CommodityDefinition> result = new ArrayList<>();
        for (CommodityDefinition def : byId.values()) {
            if (def.tier == tier) {
                result.add(def);
            }
        }
        return result;
    }

    public List<CommodityDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public void loadFromFiles() {
        Json json = new Json();
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/economy/commodities.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            CommodityDefinition def = json.readValue(CommodityDefinition.class, entry);
            register(def);
        }
    }
}
