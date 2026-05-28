package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

/** Loads and indexes {@link ThermalMaterial} definitions from data/thermal/materials.json. */
public class ThermalMaterialRegistry {
    private final Map<String, ThermalMaterial> materials = new HashMap<>();

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/thermal/materials.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ThermalMaterial m = json.readValue(ThermalMaterial.class, entry);
            materials.put(m.id, m);
        }
    }

    public ThermalMaterial get(String id) { return materials.get(id); }

    public void register(ThermalMaterial m) { materials.put(m.id, m); }
}
