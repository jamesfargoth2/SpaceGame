package com.galacticodyssey.fauna.ecosystem;

public final class SpeciesPopulation {
    public String speciesId;
    public int count;
    public float birthAccumulator;

    public SpeciesPopulation(String speciesId, int count) {
        this.speciesId = speciesId;
        this.count = count;
    }
}
