package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonReader;

import java.util.HashMap;
import java.util.Map;

/** Loads and stores {@link PilotArchetype}s by id. */
public class PilotArchetypeRegistry {

    private final Map<String, PilotArchetype> archetypes = new HashMap<>();
    private final Json json = new Json();

    /** Parse archetypes from a JSON array string (test-friendly, no Gdx.files dependency). */
    public void parse(String jsonText) {
        JsonValue root = new JsonReader().parse(jsonText);
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            PilotArchetype a = json.readValue(PilotArchetype.class, entry);
            archetypes.put(a.id, a);
        }
    }

    /** Load from the bundled resource (requires a libGDX files backend). */
    public void loadDefault() {
        parse(Gdx.files.internal("data/ai/pilot_archetypes.json").readString());
    }

    public PilotArchetype get(String id) {
        return archetypes.get(id);
    }

    public int size() {
        return archetypes.size();
    }
}
