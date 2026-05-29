package com.galacticodyssey.flora.data;

import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Per-biome flora rules: overall density + a weighted list of species ids. */
public class BiomePalette {
    public static class Entry {
        public final String speciesId;
        public final float weight;
        public Entry(String speciesId, float weight) { this.speciesId = speciesId; this.weight = weight; }
    }

    public final BiomeType biome;
    public float density;
    public float tintJitter = 0.06f;
    public final List<Entry> entries = new ArrayList<>();
    private float totalWeight;

    public BiomePalette(BiomeType biome) { this.biome = biome; }

    public void add(String speciesId, float weight) {
        entries.add(new Entry(speciesId, weight));
        totalWeight += weight;
    }

    public boolean isEmpty() { return entries.isEmpty() || totalWeight <= 0f; }

    /** Deterministic weighted choice; returns null for an empty palette. */
    public String pickSpecies(Random rng) {
        if (isEmpty()) return null;
        float r = rng.nextFloat() * totalWeight;
        for (Entry e : entries) {
            r -= e.weight;
            if (r <= 0f) return e.speciesId;
        }
        return entries.get(entries.size() - 1).speciesId; // float guard
    }
}
