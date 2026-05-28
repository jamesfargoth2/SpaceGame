package com.galacticodyssey.fauna.geometry;

/** How a part's mesh is produced — procedural primitive params, or an authored model reference. */
public final class PartGeometrySpec {
    public enum Kind { PROCEDURAL, AUTHORED }
    public enum Shape { CAPSULE, LOFT, ELLIPSOID_SNOUT, CONE }

    public Kind kind = Kind.PROCEDURAL;

    // Procedural params (kind == PROCEDURAL)
    public Shape shape = Shape.CAPSULE;
    public float length = 1f;     // along local +Z
    public float radius = 0.25f;  // base radius
    public float taper = 1f;      // tip radius / base radius

    // Authored (kind == AUTHORED)
    public String modelRef = null; // path to a .g3db node; null when procedural

    /** Approximate volume in m^3 at unit scale (used for mass). */
    public float approxVolume() {
        switch (shape) {
            case CONE:           return (float) (Math.PI * radius * radius * length / 3.0);
            case ELLIPSOID_SNOUT:return (float) (4.0 / 3.0 * Math.PI * radius * radius * (length * 0.5));
            case LOFT:
            case CAPSULE:
            default:
                float avgR = radius * (1f + taper) * 0.5f;
                return (float) (Math.PI * avgR * avgR * length); // cylinder approx
        }
    }
}
