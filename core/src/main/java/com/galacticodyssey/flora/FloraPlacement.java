package com.galacticodyssey.flora;

/** One resolved flora instance: which species/variant, where, and how oriented/scaled. */
public final class FloraPlacement {
    public final String speciesId;
    public final int variantIndex;
    public final float x, y, z;
    public final float yawDeg;
    public final float scale;

    public FloraPlacement(String speciesId, int variantIndex, float x, float y, float z,
                          float yawDeg, float scale) {
        this.speciesId = speciesId;
        this.variantIndex = variantIndex;
        this.x = x; this.y = y; this.z = z;
        this.yawDeg = yawDeg;
        this.scale = scale;
    }
}
