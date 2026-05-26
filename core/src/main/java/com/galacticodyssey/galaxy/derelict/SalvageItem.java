package com.galacticodyssey.galaxy.derelict;

/** A single salvageable item found within a derelict section. */
public final class SalvageItem {
    public final SalvageType type;
    /** Tech level / quality, normalized 0-1. */
    public final float value;

    public SalvageItem(SalvageType type, float value) {
        this.type = type;
        this.value = value;
    }
}
