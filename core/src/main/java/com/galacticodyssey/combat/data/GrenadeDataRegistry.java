package com.galacticodyssey.combat.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class GrenadeDataRegistry {
    private final Map<String, GrenadeData> grenades = new HashMap<>();

    public void loadFromFile(String path) {
        Json json = new Json();
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            GrenadeData data = json.readValue(GrenadeData.class, entry);
            grenades.put(data.id, data);
        }
    }

    public void register(GrenadeData data) {
        grenades.put(data.id, data);
    }

    public GrenadeData get(String id) {
        return grenades.get(id);
    }

    public boolean has(String id) {
        return grenades.containsKey(id);
    }

    public int size() {
        return grenades.size();
    }
}
