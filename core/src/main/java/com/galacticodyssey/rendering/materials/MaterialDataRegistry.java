package com.galacticodyssey.rendering.materials;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class MaterialDataRegistry {
    private final Map<String, MaterialData> materials = new HashMap<>();

    public void loadFromFiles(String path) {
        String content = Gdx.files.internal(path).readString();
        loadFromJson(content);
    }

    public void loadFromJson(String jsonString) {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(jsonString);
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            MaterialData data = json.readValue(MaterialData.class, entry);
            if (data.name != null) {
                materials.put(data.name, data);
            }
        }
    }

    public MaterialData getData(String name) {
        return materials.get(name);
    }

    public void register(MaterialData data) {
        materials.put(data.name, data);
    }
}
