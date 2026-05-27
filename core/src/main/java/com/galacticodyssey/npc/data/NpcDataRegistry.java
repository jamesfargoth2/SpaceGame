package com.galacticodyssey.npc.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcDataRegistry {

    public static class NamePool {
        public final List<String> firstNames;
        public final List<String> lastNames;

        public NamePool(List<String> firstNames, List<String> lastNames) {
            this.firstNames = firstNames;
            this.lastNames = lastNames;
        }
    }

    private final Map<String, SpeciesDefinition> speciesById = new HashMap<>();
    private final Map<String, BackgroundDefinition> backgroundsById = new HashMap<>();
    private final Map<String, PerkDefinition> perksById = new HashMap<>();
    private final Map<String, NamePool> namesBySpecies = new HashMap<>();

    public void registerSpecies(SpeciesDefinition def) {
        speciesById.put(def.id, def);
    }

    public void registerBackground(BackgroundDefinition def) {
        backgroundsById.put(def.id, def);
    }

    public void registerPerk(PerkDefinition def) {
        perksById.put(def.id, def);
    }

    public void registerNames(String speciesId, List<String> firstNames, List<String> lastNames) {
        namesBySpecies.put(speciesId, new NamePool(firstNames, lastNames));
    }

    public SpeciesDefinition getSpecies(String id) {
        return speciesById.get(id);
    }

    public List<SpeciesDefinition> getAllSpecies() {
        return new ArrayList<>(speciesById.values());
    }

    public List<String> getSpeciesIds() {
        return new ArrayList<>(speciesById.keySet());
    }

    public BackgroundDefinition getBackground(String id) {
        return backgroundsById.get(id);
    }

    public List<BackgroundDefinition> getAllBackgrounds() {
        return new ArrayList<>(backgroundsById.values());
    }

    public PerkDefinition getPerk(String id) {
        return perksById.get(id);
    }

    public List<PerkDefinition> getAllPerks() {
        return new ArrayList<>(perksById.values());
    }

    public NamePool getNamePool(String speciesId) {
        return namesBySpecies.get(speciesId);
    }

    public void loadFromFiles() {
        Json json = new Json();
        JsonReader reader = new JsonReader();

        JsonValue speciesRoot = reader.parse(Gdx.files.internal("data/npcs/species.json"));
        for (JsonValue entry = speciesRoot.child; entry != null; entry = entry.next) {
            registerSpecies(json.readValue(SpeciesDefinition.class, entry));
        }

        JsonValue bgRoot = reader.parse(Gdx.files.internal("data/npcs/backgrounds.json"));
        for (JsonValue entry = bgRoot.child; entry != null; entry = entry.next) {
            registerBackground(json.readValue(BackgroundDefinition.class, entry));
        }

        JsonValue perkRoot = reader.parse(Gdx.files.internal("data/npcs/perks.json"));
        for (JsonValue entry = perkRoot.child; entry != null; entry = entry.next) {
            registerPerk(json.readValue(PerkDefinition.class, entry));
        }

        JsonValue namesRoot = reader.parse(Gdx.files.internal("data/npcs/names.json"));
        for (JsonValue species = namesRoot.child; species != null; species = species.next) {
            List<String> firsts = new ArrayList<>();
            List<String> lasts = new ArrayList<>();
            for (JsonValue n = species.get("first").child; n != null; n = n.next) {
                firsts.add(n.asString());
            }
            for (JsonValue n = species.get("last").child; n != null; n = n.next) {
                lasts.add(n.asString());
            }
            registerNames(species.name, firsts, lasts);
        }
    }
}
