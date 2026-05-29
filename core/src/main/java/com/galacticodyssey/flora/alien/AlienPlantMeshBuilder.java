package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

import java.util.Random;

/** Builds a stalk + archetype canopy + details into GL-free {@link AlienPlantMeshData}. */
public final class AlienPlantMeshBuilder {
    public static final int STRIDE = 11;
    private AlienPlantMeshBuilder() {}

    public static AlienPlantMeshData build(AlienPlantSpecies sp, Random rng) {
        FloatArray v = new FloatArray();
        ShortArray idx = new ShortArray();
        BoundingBox b = new BoundingBox();
        b.inf();

        float height = lerp(sp.stalkHeightMin, sp.stalkHeightMax, rng.nextFloat());
        float baseR = sp.stalkBaseRadius;
        float topR = Math.max(0.02f, baseR * sp.stalkTaper);
        Vector3 base = new Vector3(0, 0, 0);
        Vector3 top = new Vector3(0, height, 0);
        // stalk
        addTube(v, idx, base, baseR, top, topR, sp.stalkSides, sp.stalkColor, 0f, b);

        switch (sp.archetype) {
            case BIOLUMINESCENT: buildBiolum(v, idx, sp, top, height, rng, b); break;
            case CARNIVOROUS:    buildCarnivorous(v, idx, sp, top, rng, b); break;
            case CRYSTAL:        buildCrystal(v, idx, sp, top, rng, b); break;
        }
        return new AlienPlantMeshData(v.toArray(), idx.toArray(), b);
    }

    private static void buildBiolum(FloatArray v, ShortArray idx, AlienPlantSpecies sp,
                                    Vector3 top, float height, Random rng, BoundingBox b) {
        int clumps = irange(sp.clumpsMin, sp.clumpsMax, rng);
        for (int i = 0; i < clumps; i++) {
            float r = lerp(sp.clumpRadiusMin, sp.clumpRadiusMax, rng.nextFloat());
            Vector3 c = new Vector3(top).add(
                (rng.nextFloat() - 0.5f) * r * 1.5f,
                rng.nextFloat() * r,
                (rng.nextFloat() - 0.5f) * r * 1.5f);
            addBlob(v, idx, c, r, 4, 6, sp.canopyColor, sp.canopyEmissive, b);
        }
        int details = irange(sp.detailCountMin, sp.detailCountMax, rng);
        for (int i = 0; i < details; i++) {
            float r = 0.04f + rng.nextFloat() * 0.06f;
            float t = 0.2f + rng.nextFloat() * 0.7f;
            Vector3 c = new Vector3((rng.nextFloat() - 0.5f) * 0.1f, height * t, (rng.nextFloat() - 0.5f) * 0.1f);
            addBlob(v, idx, c, r, 3, 5, sp.canopyColor, sp.detailEmissive, b);
        }
    }

    private static void buildCarnivorous(FloatArray v, ShortArray idx, AlienPlantSpecies sp,
                                         Vector3 top, Random rng, BoundingBox b) {
        float mouthR = lerp(sp.mouthRadiusMin, sp.mouthRadiusMax, rng.nextFloat());
        float depth = lerp(sp.canopyDepthMin, sp.canopyDepthMax, rng.nextFloat());
        Vector3 cupTop = new Vector3(top).add(0, depth, 0);
        // pitcher cup: narrow at stalk, flaring to mouth
        addTube(v, idx, top, mouthR * 0.25f, cupTop, mouthR, 10, sp.canopyColor, 0f, b);
        // lip ring (short wide rim)
        Vector3 lipTop = new Vector3(cupTop).add(0, depth * 0.12f, 0);
        addTube(v, idx, cupTop, mouthR, lipTop, mouthR * 1.1f, 10, sp.canopyColor, 0f, b);
        // lure inside the cup (glows)
        Vector3 lure = new Vector3(top).add(0, depth * 0.5f, 0);
        addBlob(v, idx, lure, mouthR * 0.2f, 3, 5, sp.canopyColor, sp.lureEmissive, b);
        // teeth: thin inward-pointing cones around the rim
        int teeth = irange(sp.teethMin, sp.teethMax, rng);
        for (int i = 0; i < teeth; i++) {
            double a = 2.0 * Math.PI * i / Math.max(1, teeth);
            Vector3 rimPt = new Vector3(cupTop).add(mouthR * (float) Math.cos(a), 0, mouthR * (float) Math.sin(a));
            Vector3 tip = new Vector3(rimPt).add(-mouthR * 0.3f * (float) Math.cos(a), depth * 0.2f, -mouthR * 0.3f * (float) Math.sin(a));
            addTube(v, idx, rimPt, mouthR * 0.06f, tip, 0.005f, 4, sp.stalkColor, 0f, b);
        }
    }

    private static void buildCrystal(FloatArray v, ShortArray idx, AlienPlantSpecies sp,
                                     Vector3 top, Random rng, BoundingBox b) {
        int shards = irange(sp.shardsMin, sp.shardsMax, rng);
        for (int i = 0; i < shards; i++) {
            spawnShard(v, idx, top, lerp(sp.shardLenMin, sp.shardLenMax, rng.nextFloat()),
                0.08f + rng.nextFloat() * 0.08f, sp, rng, b);
        }
        int sub = irange(sp.subShardsMin, sp.subShardsMax, rng);
        for (int i = 0; i < sub; i++) {
            Vector3 origin = new Vector3(top).add((rng.nextFloat() - 0.5f) * 0.3f, rng.nextFloat() * 0.2f, (rng.nextFloat() - 0.5f) * 0.3f);
            spawnShard(v, idx, origin, lerp(sp.shardLenMin, sp.shardLenMax, rng.nextFloat()) * 0.5f,
                0.04f + rng.nextFloat() * 0.04f, sp, rng, b);
        }
    }

    private static void spawnShard(FloatArray v, ShortArray idx, Vector3 origin, float len, float r,
                                   AlienPlantSpecies sp, Random rng, BoundingBox b) {
        // direction: mostly up, splayed outward
        Vector3 dir = new Vector3((rng.nextFloat() - 0.5f) * 1.4f, 0.6f + rng.nextFloat() * 0.8f, (rng.nextFloat() - 0.5f) * 1.4f).nor();
        Vector3 tip = new Vector3(origin).mulAdd(dir, len);
        // a faceted shard: wide-ish base ring tapering to a point (prism->point)
        addTube(v, idx, origin, r, tip, 0.01f, 5, sp.canopyColor, sp.canopyEmissive, b);
    }

    // ---- geometry helpers (write stride-11 vertices) ----

    /** Tapered N-gon tube from a (radius ra) to b (radius rb). */
    private static void addTube(FloatArray v, ShortArray idx, Vector3 a, float ra,
                                Vector3 b, float rb, int sides, Color col, float emissive, BoundingBox bb) {
        Vector3 dir = new Vector3(b).sub(a);
        if (dir.len2() < 1e-8f) return;
        dir.nor();
        Vector3 up = Math.abs(dir.y) < 0.99f ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 t1 = new Vector3(up).crs(dir).nor();
        Vector3 t2 = new Vector3(dir).crs(t1).nor();
        int base = v.size / STRIDE;
        for (int s = 0; s < sides; s++) {
            double ang = 2.0 * Math.PI * s / sides;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            Vector3 radial = new Vector3(t1).scl(cos).add(new Vector3(t2).scl(sin)).nor();
            vert(v, a.x + radial.x * ra, a.y + radial.y * ra, a.z + radial.z * ra, radial, col, emissive, bb);
            vert(v, b.x + radial.x * rb, b.y + radial.y * rb, b.z + radial.z * rb, radial, col, emissive, bb);
        }
        for (int s = 0; s < sides; s++) {
            int sn = (s + 1) % sides;
            short b0 = (short) (base + 2 * s), t0 = (short) (base + 2 * s + 1);
            short b1 = (short) (base + 2 * sn), tt = (short) (base + 2 * sn + 1);
            idx.add(b0); idx.add(t0); idx.add(b1);
            idx.add(b1); idx.add(t0); idx.add(tt);
        }
    }

    /** Low-poly UV-sphere blob. */
    private static void addBlob(FloatArray v, ShortArray idx, Vector3 c, float r,
                                int rings, int sectors, Color col, float emissive, BoundingBox bb) {
        int base = v.size / STRIDE;
        for (int ri = 0; ri <= rings; ri++) {
            double phi = Math.PI * ri / rings;
            float y = (float) Math.cos(phi), rad = (float) Math.sin(phi);
            for (int si = 0; si <= sectors; si++) {
                double th = 2.0 * Math.PI * si / sectors;
                float x = rad * (float) Math.cos(th), z = rad * (float) Math.sin(th);
                Vector3 n = new Vector3(x, y, z);
                vert(v, c.x + x * r, c.y + y * r, c.z + z * r, n, col, emissive, bb);
            }
        }
        int stride = sectors + 1;
        for (int ri = 0; ri < rings; ri++) for (int si = 0; si < sectors; si++) {
            short p0 = (short) (base + ri * stride + si);
            short p1 = (short) (base + (ri + 1) * stride + si);
            short p2 = (short) (base + ri * stride + si + 1);
            short p3 = (short) (base + (ri + 1) * stride + si + 1);
            idx.add(p0); idx.add(p1); idx.add(p2);
            idx.add(p2); idx.add(p1); idx.add(p3);
        }
    }

    private static void vert(FloatArray v, float x, float y, float z, Vector3 n, Color c, float emissive, BoundingBox bb) {
        v.add(x); v.add(y); v.add(z);
        v.add(n.x); v.add(n.y); v.add(n.z);
        v.add(c.r); v.add(c.g); v.add(c.b); v.add(c.a);
        v.add(emissive);
        bb.ext(x, y, z);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int irange(int lo, int hi, Random rng) { return hi <= lo ? lo : lo + rng.nextInt(hi - lo + 1); }
}
