package com.galacticodyssey.hacking.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.hacking.HackableComponent;

import java.util.HashMap;
import java.util.Map;

public class HackableTypeRegistry {

    private final Map<String, HackableTypeData> types = new HashMap<>();

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/hacking/hackable_types.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            HackableTypeData data = json.readValue(HackableTypeData.class, entry);
            data.id = entry.name;
            types.put(data.id, data);
        }
    }

    public void configure(HackableComponent component) {
        HackableTypeData data = types.get(component.typeId);
        if (data == null) return;
        component.difficulty = data.difficulty;
        component.effect = data.effect;
        component.lockoutDuration = data.lockoutDuration;
        component.requiresPhysicalAccess = data.requiresPhysicalAccess;
        component.interactionRange = data.interactionRange;
    }

    public HackableTypeData get(String id) { return types.get(id); }

    public void register(HackableTypeData data) { types.put(data.id, data); }
}
