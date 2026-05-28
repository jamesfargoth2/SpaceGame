package com.galacticodyssey.crafting.data;

import com.galacticodyssey.crafting.MaterialCategory;
import com.galacticodyssey.crafting.MaterialTier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores {@link MaterialDefinition}s by ID. Supports lookup by ID, tier, and category.
 * Populated programmatically in tests; loaded from JSON data files at runtime.
 */
public class MaterialRegistry {
    private final Map<String, MaterialDefinition> byId = new HashMap<>();

    /**
     * Registers a material definition. Throws if a material with the same ID already exists.
     */
    public void register(MaterialDefinition definition) {
        if (byId.containsKey(definition.materialId)) {
            throw new IllegalArgumentException(
                "Duplicate material ID: " + definition.materialId);
        }
        byId.put(definition.materialId, definition);
    }

    /** Returns the definition for the given ID, or null if not found. */
    public MaterialDefinition get(String materialId) {
        return byId.get(materialId);
    }

    /** Returns true if a material with the given ID is registered. */
    public boolean contains(String materialId) {
        return byId.containsKey(materialId);
    }

    /** Returns all materials matching the given tier. */
    public List<MaterialDefinition> getByTier(MaterialTier tier) {
        List<MaterialDefinition> result = new ArrayList<>();
        for (MaterialDefinition def : byId.values()) {
            if (def.tier == tier) {
                result.add(def);
            }
        }
        return result;
    }

    /** Returns all materials matching the given category. */
    public List<MaterialDefinition> getByCategory(MaterialCategory category) {
        List<MaterialDefinition> result = new ArrayList<>();
        for (MaterialDefinition def : byId.values()) {
            if (def.category == category) {
                result.add(def);
            }
        }
        return result;
    }

    /** Returns all registered material definitions. */
    public List<MaterialDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    /**
     * Loads material definitions from {@code data/crafting/materials.json}.
     * Requires a libGDX context (not usable in unit tests).
     */
    public void loadFromFile() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/materials.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            MaterialDefinition def = json.readValue(MaterialDefinition.class, entry);
            register(def);
        }
    }
}
