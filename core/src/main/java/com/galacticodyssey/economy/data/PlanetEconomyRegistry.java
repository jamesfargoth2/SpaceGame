package com.galacticodyssey.economy.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanetEconomyRegistry {
    private final Map<String, PlanetEconomyData> byPlanetId = new HashMap<>();

    public void register(PlanetEconomyData data) {
        byPlanetId.put(data.planetId, data);
    }

    public PlanetEconomyData get(String planetId) {
        return byPlanetId.get(planetId);
    }

    public List<PlanetEconomyData> getAll() {
        return new ArrayList<>(byPlanetId.values());
    }

    public void loadFromFiles() {
        Json json = new Json();
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/economy/planet_economies.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            PlanetEconomyData data = json.readValue(PlanetEconomyData.class, entry);
            register(data);
        }
    }
}
