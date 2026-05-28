package com.galacticodyssey.crafting.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefineryModuleRegistry {
    private final Map<String, RefineryModuleDefinition> byId = new HashMap<>();

    public void register(RefineryModuleDefinition definition) {
        if (byId.containsKey(definition.moduleId)) {
            throw new IllegalArgumentException("Duplicate module ID: " + definition.moduleId);
        }
        byId.put(definition.moduleId, definition);
    }

    public RefineryModuleDefinition get(String moduleId) {
        return byId.get(moduleId);
    }

    public List<RefineryModuleDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public void loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/refinery_modules.json"));
        JsonValue modules = root.get("refineryModules");
        for (JsonValue entry = modules.child; entry != null; entry = entry.next) {
            register(new RefineryModuleDefinition(
                entry.getString("moduleId"),
                entry.getString("name"),
                entry.getInt("tier"),
                entry.getInt("maxQueueSize"),
                entry.getFloat("speedMultiplier"),
                entry.getFloat("powerDraw"),
                entry.getFloat("weight"),
                entry.getInt("cost")
            ));
        }
    }
}
