package com.galacticodyssey.flora;

/** Shared enums for flora generation. */
public final class FloraEnums {
    private FloraEnums() {}

    /** Coarse shape of the attraction-point volume a plant grows into. */
    public enum EnvelopeShape { ELLIPSOID, CONE, COLUMN, DOME, CYLINDER }

    /** How (or whether) foliage clumps are attached to branch tips. */
    public enum FoliageStyle { CLUMP, NONE }
}
