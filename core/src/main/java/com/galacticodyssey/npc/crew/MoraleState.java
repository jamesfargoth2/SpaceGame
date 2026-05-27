package com.galacticodyssey.npc.crew;

public enum MoraleState {
    MUTINOUS(0, 24),
    DISGRUNTLED(25, 49),
    GRUMBLING(50, 79),
    CONTENT(80, 100);

    public final int minMorale;
    public final int maxMorale;

    MoraleState(int minMorale, int maxMorale) {
        this.minMorale = minMorale;
        this.maxMorale = maxMorale;
    }

    public static MoraleState fromMorale(float morale) {
        if (morale >= 80f) return CONTENT;
        if (morale >= 50f) return GRUMBLING;
        if (morale >= 25f) return DISGRUNTLED;
        return MUTINOUS;
    }

    public float effectivenessModifier() {
        switch (this) {
            case CONTENT:     return 1.1f;
            case GRUMBLING:   return 1.0f;
            case DISGRUNTLED: return 0.85f;
            case MUTINOUS:    return 0.7f;
            default:          return 1.0f;
        }
    }
}
