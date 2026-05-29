package com.galacticodyssey.flora.alien;

/** One resolved alien-plant instance. */
public final class AlienPlantPlacement {
    public final String speciesId;
    public final int variantIndex;
    public final float x, y, z, yawDeg, scale;
    public AlienPlantPlacement(String speciesId, int variantIndex, float x, float y, float z, float yawDeg, float scale) {
        this.speciesId = speciesId; this.variantIndex = variantIndex;
        this.x = x; this.y = y; this.z = z; this.yawDeg = yawDeg; this.scale = scale;
    }
}
