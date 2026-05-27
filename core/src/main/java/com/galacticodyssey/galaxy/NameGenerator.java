package com.galacticodyssey.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import java.util.Random;

public final class NameGenerator {

    private String[] starOnsets;
    private String[] starCodas;
    private String[] greekLetters;
    private String[] planetRoots;
    private String[] planetSuffixes;
    private String[] stationAdjectives;
    private String[] stationNouns;
    private String[] factionAdjectives;
    private String[] factionNouns;
    private String[] factionPrefixes;
    private String[] shipClasses;
    private String[] shipNames;
    private String[] personOnsets;
    private String[] personCodas;
    private String[] romanNumerals;

    public NameGenerator() {
        loadData();
    }

    private void loadData() {
        Json json = new Json();
        JsonValue syllables = json.fromJson(null, Gdx.files.internal("data/names/syllables.json"));
        starOnsets = toArray(syllables.get("starOnsets"));
        starCodas = toArray(syllables.get("starCodas"));
        greekLetters = toArray(syllables.get("greekLetters"));
        planetRoots = toArray(syllables.get("planetRoots"));
        planetSuffixes = toArray(syllables.get("planetSuffixes"));
        stationAdjectives = toArray(syllables.get("stationAdjectives"));
        stationNouns = toArray(syllables.get("stationNouns"));
        factionAdjectives = toArray(syllables.get("factionAdjectives"));
        factionNouns = toArray(syllables.get("factionNouns"));
        factionPrefixes = toArray(syllables.get("factionPrefixes"));
        shipClasses = toArray(syllables.get("shipClasses"));
        shipNames = toArray(syllables.get("shipNames"));
        personOnsets = toArray(syllables.get("personOnsets"));
        personCodas = toArray(syllables.get("personCodas"));

        JsonValue prefixes = json.fromJson(null, Gdx.files.internal("data/names/prefixes.json"));
        romanNumerals = toArray(prefixes.get("romanNumerals"));
    }

    private String[] toArray(JsonValue arrayNode) {
        String[] result = new String[arrayNode.size];
        for (int i = 0; i < arrayNode.size; i++) {
            result[i] = arrayNode.getString(i);
        }
        return result;
    }

    public GeneratedName generateStarName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 0);
        Random rng = new Random(nameSeed);

        String onset = pick(rng, starOnsets);
        String coda = pick(rng, starCodas);
        String root = onset + coda;

        String prefix = "";
        if (rng.nextFloat() < 0.3f) {
            prefix = pick(rng, greekLetters);
        }

        return new GeneratedName(prefix, root, "");
    }

    public GeneratedName generatePlanetName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 1);
        Random rng = new Random(nameSeed);

        String root = pick(rng, planetRoots) + pick(rng, planetSuffixes);
        String suffix = "";
        if (rng.nextFloat() < 0.4f) {
            suffix = pick(rng, romanNumerals);
        }

        return new GeneratedName("", root, suffix);
    }

    public GeneratedName generateStationName(long seed, String stationType) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 2);
        Random rng = new Random(nameSeed);

        String adj = pick(rng, stationAdjectives);
        String noun = pick(rng, stationNouns);

        return new GeneratedName(adj, noun, "");
    }

    public GeneratedName generateFactionName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 3);
        Random rng = new Random(nameSeed);

        String prefix = pick(rng, factionPrefixes);
        String adj = pick(rng, factionAdjectives);
        String noun = pick(rng, factionNouns);

        return new GeneratedName(prefix, adj, noun);
    }

    public GeneratedName generateShipName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 4);
        Random rng = new Random(nameSeed);

        String className = pick(rng, shipClasses);
        String name = pick(rng, shipNames);

        return new GeneratedName(className + "-class", name, "");
    }

    public GeneratedName generatePersonName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 5);
        Random rng = new Random(nameSeed);

        String first = pick(rng, personOnsets) + pick(rng, personCodas);
        String last = pick(rng, personOnsets) + pick(rng, personCodas);

        return new GeneratedName("", first, last);
    }

    private String pick(Random rng, String[] array) {
        return array[rng.nextInt(array.length)];
    }
}
