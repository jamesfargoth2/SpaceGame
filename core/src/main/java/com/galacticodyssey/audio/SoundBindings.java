package com.galacticodyssey.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

public class SoundBindings {

    private static final String BINDINGS_PATH = "data/audio/sound_bindings.json";

    private final Map<String, String> bindings = new HashMap<>();

    public void load() {
        if (Gdx.files == null) return;
        FileHandle file = Gdx.files.internal(BINDINGS_PATH);
        if (!file.exists()) {
            Gdx.app.log("SoundBindings", "Not found: " + BINDINGS_PATH);
            return;
        }
        JsonValue root = new JsonReader().parse(file);
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            bindings.put(entry.name, entry.asString());
        }
        Gdx.app.log("SoundBindings", "Loaded " + bindings.size() + " bindings");
    }

    /** Exact key lookup, then progressively drops the last colon-segment as fallback. */
    public String resolveWithFallback(String key) {
        String current = key;
        while (current != null && !current.isEmpty()) {
            String path = bindings.get(current);
            if (path != null) return path;
            int lastColon = current.lastIndexOf(':');
            current = lastColon >= 0 ? current.substring(0, lastColon) : null;
        }
        return null;
    }

    /** Register a binding at runtime (used in tests or programmatic setup). */
    public void register(String key, String path) {
        bindings.put(key, path);
    }
}
