package com.galacticodyssey.crafting;

public enum MaterialTier {
    RAW(1),
    PROCESSED(2),
    REFINED(3);

    public final int level;

    MaterialTier(int level) {
        this.level = level;
    }
}
