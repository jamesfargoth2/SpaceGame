package com.galacticodyssey.fauna.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds simple, deterministic procedural part meshes (math only, no GL). A part runs along its
 * local +Z axis from 0..length; radius is interpolated base->tip by {@code taper}.
 */
public final class ProceduralPartMesher {
    private static final int RADIAL = 8;   // segments around the axis
    private static final int RINGS  = 4;   // rings along the axis

    public ProceduralMeshData build(PartGeometrySpec spec) {
        List<Float> pos = new ArrayList<>();
        List<Short> idx = new ArrayList<>();

        for (int ring = 0; ring <= RINGS; ring++) {
            float t = ring / (float) RINGS;
            float z = t * spec.length;
            float r = spec.radius * (1f + (spec.taper - 1f) * t);
            // ELLIPSOID_SNOUT bulges in the middle then tapers; CONE tapers to a point
            if (spec.shape == PartGeometrySpec.Shape.ELLIPSOID_SNOUT)
                r = spec.radius * (float) Math.sin(Math.PI * Math.max(0.05f, t));
            else if (spec.shape == PartGeometrySpec.Shape.CONE)
                r = spec.radius * (1f - t);
            for (int s = 0; s < RADIAL; s++) {
                double a = 2.0 * Math.PI * s / RADIAL;
                pos.add((float) (Math.cos(a) * r));
                pos.add((float) (Math.sin(a) * r));
                pos.add(z);
            }
        }
        for (int ring = 0; ring < RINGS; ring++) {
            for (int s = 0; s < RADIAL; s++) {
                int s2 = (s + 1) % RADIAL;
                short a = (short) (ring * RADIAL + s);
                short b = (short) (ring * RADIAL + s2);
                short c = (short) ((ring + 1) * RADIAL + s);
                short d = (short) ((ring + 1) * RADIAL + s2);
                idx.add(a); idx.add(c); idx.add(b);
                idx.add(b); idx.add(c); idx.add(d);
            }
        }

        float[] p = new float[pos.size()];
        for (int i = 0; i < p.length; i++) p[i] = pos.get(i);
        short[] ix = new short[idx.size()];
        for (int i = 0; i < ix.length; i++) ix[i] = idx.get(i);
        return new ProceduralMeshData(p, ix);
    }
}
