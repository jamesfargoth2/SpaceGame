package com.galacticodyssey.data;

public enum AssetCategory {
    CHARACTER(10f),
    PROP_SMALL(6f),
    PROP_LARGE(5f),
    INTERIOR_PROP(8f),
    FOLIAGE(4f),
    BUILDING(3f),
    VFX_MESH(7f),
    TEXTURE_ATLAS(9f);

    /** Higher weight = loaded sooner relative to distance. */
    public final float priorityWeight;

    AssetCategory(float priorityWeight) {
        this.priorityWeight = priorityWeight;
    }
}
