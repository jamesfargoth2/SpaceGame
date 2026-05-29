package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import com.galacticodyssey.flora.data.FloraSpecies;

import java.util.Random;

/** Turns a {@link BranchSkeleton} into GL-free {@link FloraMeshData}. No GL context required. */
public final class FloraMeshBuilder {
    private static final int FOLIAGE_RINGS = 4;
    private static final int FOLIAGE_SECTORS = 6;

    private FloraMeshBuilder() {}

    public static FloraMeshData build(BranchSkeleton skel, FloraSpecies sp, Random rng) {
        FloatArray tv = new FloatArray();
        ShortArray ti = new ShortArray();
        FloatArray fv = new FloatArray();
        ShortArray fi = new ShortArray();
        BoundingBox bounds = new BoundingBox();
        bounds.inf();

        // Trunk + branches: a tapered tube per (child -> parent) segment.
        for (int i = 1; i < skel.size(); i++) {
            int p = skel.parent(i);
            Vector3 a = skel.position(p);
            Vector3 b = skel.position(i);
            float ra = relToWorld(sp, skel.relRadius(p));
            float rb = relToWorld(sp, skel.relRadius(i));
            addSegment(tv, ti, a, ra, b, rb, sp.trunkSides, bounds);
        }

        // Foliage clumps at tips. Indices are 16-bit (short), so cap the foliage vertex
        // count: a single mesh part cannot reference more than Short.MAX_VALUE vertices.
        // Stop adding blobs once another one would overflow rather than silently corrupt
        // the index buffer (only reachable with very aggressive clumpsPerTip/maxNodes).
        if (sp.foliageStyle == FoliageStyle.CLUMP) {
            int blobVerts = (FOLIAGE_RINGS + 1) * (FOLIAGE_SECTORS + 1);
            outer:
            for (int i = 0; i < skel.size(); i++) {
                if (!skel.isTip(i)) continue;
                for (int c = 0; c < sp.clumpsPerTip; c++) {
                    if (fv.size / 6 + blobVerts > Short.MAX_VALUE) break outer;
                    float r = sp.clumpRadiusMin + rng.nextFloat() * (sp.clumpRadiusMax - sp.clumpRadiusMin);
                    Vector3 center = new Vector3(skel.position(i));
                    center.add((rng.nextFloat() - 0.5f) * r, (rng.nextFloat() - 0.5f) * r * 0.5f,
                               (rng.nextFloat() - 0.5f) * r);
                    addBlob(fv, fi, center, r, FOLIAGE_RINGS, FOLIAGE_SECTORS, bounds);
                }
            }
        }

        return new FloraMeshData(tv.toArray(), ti.toArray(), fv.toArray(), fi.toArray(), bounds);
    }

    private static float relToWorld(FloraSpecies sp, float rel) {
        // taper widens or narrows the mid-branch profile; baseRadius scales the whole tree.
        return Math.max(0.02f, sp.baseRadius * (sp.taper * rel + (1f - sp.taper) * rel * rel));
    }

    /**
     * Tapered N-gon tube from a (radius ra) to b (radius rb). Ring normals are purely
     * radial (cylindrical) — the small axial component from taper is ignored, which is an
     * acceptable approximation at these low poly counts. The downstream model factory may
     * recompute normals if smoother shading is ever needed.
     */
    private static void addSegment(FloatArray v, ShortArray idx, Vector3 a, float ra,
                                   Vector3 b, float rb, int sides, BoundingBox bounds) {
        Vector3 dir = new Vector3(b).sub(a);
        if (dir.len2() < 1e-8f) return;
        dir.nor();
        Vector3 up = Math.abs(dir.y) < 0.99f ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 t1 = new Vector3(up).crs(dir).nor();
        Vector3 t2 = new Vector3(dir).crs(t1).nor();

        int base = v.size / 6;
        for (int s = 0; s < sides; s++) {
            double ang = 2.0 * Math.PI * s / sides;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            Vector3 radial = new Vector3(t1).scl(cos).add(new Vector3(t2).scl(sin)).nor();
            addVertex(v, a.x + radial.x * ra, a.y + radial.y * ra, a.z + radial.z * ra, radial, bounds);
            addVertex(v, b.x + radial.x * rb, b.y + radial.y * rb, b.z + radial.z * rb, radial, bounds);
        }
        for (int s = 0; s < sides; s++) {
            int sn = (s + 1) % sides;
            short b0 = (short) (base + 2 * s), t0 = (short) (base + 2 * s + 1);
            short b1 = (short) (base + 2 * sn), tt = (short) (base + 2 * sn + 1);
            idx.add(b0); idx.add(t0); idx.add(b1);
            idx.add(b1); idx.add(t0); idx.add(tt);
        }
    }

    /** Low-poly UV-sphere foliage blob centred at c. */
    private static void addBlob(FloatArray v, ShortArray idx, Vector3 c, float r,
                                int rings, int sectors, BoundingBox bounds) {
        int base = v.size / 6;
        for (int ri = 0; ri <= rings; ri++) {
            double phi = Math.PI * ri / rings;
            float y = (float) Math.cos(phi), rad = (float) Math.sin(phi);
            for (int si = 0; si <= sectors; si++) {
                double theta = 2.0 * Math.PI * si / sectors;
                float x = rad * (float) Math.cos(theta), z = rad * (float) Math.sin(theta);
                Vector3 nrm = new Vector3(x, y, z); // already unit
                addVertex(v, c.x + x * r, c.y + y * r, c.z + z * r, nrm, bounds);
            }
        }
        int stride = sectors + 1;
        for (int ri = 0; ri < rings; ri++) {
            for (int si = 0; si < sectors; si++) {
                short p0 = (short) (base + ri * stride + si);
                short p1 = (short) (base + (ri + 1) * stride + si);
                short p2 = (short) (base + ri * stride + si + 1);
                short p3 = (short) (base + (ri + 1) * stride + si + 1);
                idx.add(p0); idx.add(p1); idx.add(p2);
                idx.add(p2); idx.add(p1); idx.add(p3);
            }
        }
    }

    private static void addVertex(FloatArray v, float x, float y, float z, Vector3 n, BoundingBox bounds) {
        v.add(x); v.add(y); v.add(z);
        v.add(n.x); v.add(n.y); v.add(n.z);
        bounds.ext(x, y, z);
    }
}
