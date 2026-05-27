package com.galacticodyssey.combat;

public final class EncounterEntry {
    public final EncounterType type;
    public final float weight;
    public final int minDifficulty;
    public final int maxDifficulty;

    public EncounterEntry(EncounterType type, float weight, int minDifficulty, int maxDifficulty) {
        this.type = type;
        this.weight = weight;
        this.minDifficulty = minDifficulty;
        this.maxDifficulty = maxDifficulty;
    }
}
