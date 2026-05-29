package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/** Static-snapshot tectonic model: spatial plate queries, boundary classification,
 *  macro-elevation field, and exported features. Pure logic; no GL/Gdx context. */
public final class TectonicModel {
    private final List<Plate> plates;
    private final List<Vector3> hotspots;
    private final TectonicConfig config;

    private float continentalFraction;
    private final List<TectonicFeature> features = new ArrayList<>();

    // Scratch vectors (single-threaded generation/query).
    private final Vector3 va = new Vector3();
    private final Vector3 vb = new Vector3();
    private final Vector3 n = new Vector3();
    private final Vector3 rel = new Vector3();

    public TectonicModel(List<Plate> plates, List<Vector3> hotspots, TectonicConfig config) {
        if (plates == null || plates.isEmpty()) {
            throw new IllegalArgumentException("TectonicModel requires at least one plate");
        }
        this.plates = plates;
        this.hotspots = hotspots;
        this.config = config;
        bakeContinentalFraction(); // Task 5
        bakeFeatures();            // Task 5
    }

    public int plateAt(Vector3 dir) {
        return nearest(dir).id;
    }

    public boolean isOceanic(int plateId) {
        for (Plate p : plates) if (p.id == plateId) return p.oceanic;
        return false;
    }

    public BoundaryQuery boundaryAt(Vector3 dir) {
        Plate a = null, b = null;
        float bestDot = -2f, secondDot = -2f;
        for (Plate p : plates) {
            float d = dir.dot(p.center);
            if (d > bestDot) { secondDot = bestDot; b = a; bestDot = d; a = p; }
            else if (d > secondDot) { secondDot = d; b = p; }
        }
        if (a == null || b == null) return new BoundaryQuery(BoundaryType.NONE, 1f);

        float angA = (float) Math.acos(MathUtils.clamp(bestDot, -1f, 1f));
        float angB = (float) Math.acos(MathUtils.clamp(secondDot, -1f, 1f));
        float half = 0.5f * (angB - angA);
        float dn = MathUtils.clamp(half / config.boundaryInfluence, 0f, 1f);
        if (dn >= 1f) return new BoundaryQuery(BoundaryType.NONE, 1f);

        // Tangential boundary normal pointing from B's center toward A's center.
        n.set(a.center).sub(b.center);
        n.mulAdd(dir, -n.dot(dir)); // project onto tangent plane at dir
        if (n.len2() < 1e-12f) return new BoundaryQuery(BoundaryType.TRANSFORM, dn);
        n.nor();

        a.velocityAt(dir, va);
        b.velocityAt(dir, vb);
        rel.set(va).sub(vb);
        float normalComp = rel.dot(n);
        float tangMag = (float) Math.sqrt(Math.max(0f, rel.len2() - normalComp * normalComp));

        BoundaryType type;
        if (Math.abs(normalComp) >= tangMag) {
            if (normalComp < 0f) {
                type = (!a.oceanic && !b.oceanic)
                        ? BoundaryType.CONVERGENT_CONTINENTAL
                        : BoundaryType.CONVERGENT_OCEANIC;
            } else {
                type = BoundaryType.DIVERGENT;
            }
        } else {
            type = BoundaryType.TRANSFORM;
        }
        return new BoundaryQuery(type, dn);
    }

    Plate nearest(Vector3 dir) {
        Plate best = null;
        float bestDot = -2f;
        for (Plate p : plates) {
            float d = dir.dot(p.center);
            if (d > bestDot) { bestDot = d; best = p; }
        }
        return best;
    }

    public float baseElevation(Vector3 dir) {
        Plate p = nearest(dir);
        float e = p.baseElevation;

        BoundaryQuery q = boundaryAt(dir);
        float falloff = 1f - q.distanceNormalized;      // 1 at boundary, 0 at edge
        float s = falloff * falloff * (3f - 2f * falloff); // smoothstep
        switch (q.type) {
            case CONVERGENT_CONTINENTAL -> e += config.mountainUplift * s;
            case CONVERGENT_OCEANIC -> e -= config.trenchDepth * s;
            case DIVERGENT -> e += (p.oceanic ? config.ridgeUplift : -config.riftDepth) * s;
            default -> { /* TRANSFORM/NONE: no elevation change */ }
        }

        for (Vector3 h : hotspots) {
            float ang = (float) Math.acos(MathUtils.clamp(dir.dot(h), -1f, 1f));
            if (ang < config.hotspotInfluence) {
                float hf = 1f - ang / config.hotspotInfluence;
                e += config.hotspotUplift * (hf * hf * (3f - 2f * hf));
            }
        }
        return e;
    }

    public int plateCount() { return plates.size(); }

    public float continentalFraction() { return continentalFraction; }

    public List<TectonicFeature> features() { return java.util.Collections.unmodifiableList(features); }

    private static final int SAMPLE_COUNT = 600;

    private void bakeContinentalFraction() {
        int land = 0;
        Vector3 d = new Vector3();
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            fibSphere(i, SAMPLE_COUNT, d);
            if (nearest(d).baseElevation >= 0f) land++;
        }
        continentalFraction = land / (float) SAMPLE_COUNT;
    }

    private void bakeFeatures() {
        for (Vector3 h : hotspots) {
            features.add(new TectonicFeature(FeatureType.HOTSPOT, h.cpy().nor()));
        }
        List<Vector3> placed = new ArrayList<>();
        Vector3 d = new Vector3();
        float minSpacing = config.boundaryInfluence * 2f;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            fibSphere(i, SAMPLE_COUNT, d);
            BoundaryQuery q = boundaryAt(d);
            if (q.distanceNormalized > 0.25f) continue;
            FeatureType ft;
            switch (q.type) {
                case CONVERGENT_OCEANIC -> ft = FeatureType.VOLCANIC_ARC;
                case DIVERGENT -> ft = FeatureType.RIFT;
                default -> { continue; }
            }
            boolean tooClose = false;
            for (Vector3 placedDir : placed) {
                if (Math.acos(MathUtils.clamp(placedDir.dot(d), -1f, 1f)) < minSpacing) { tooClose = true; break; }
            }
            if (tooClose) continue;
            Vector3 pos = d.cpy().nor();
            placed.add(pos);
            features.add(new TectonicFeature(ft, pos));
            if (ft == FeatureType.VOLCANIC_ARC) {
                features.add(new TectonicFeature(FeatureType.TRENCH, pos)); // colocated trench
            }
        }
    }

    /** Deterministic evenly-distributed point i of n on the unit sphere (Fibonacci spiral). */
    private static void fibSphere(int i, int n, Vector3 out) {
        float ga = MathUtils.PI * (3f - (float) Math.sqrt(5.0)); // golden angle
        float y = 1f - 2f * (i + 0.5f) / n;
        float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float theta = ga * i;
        out.set(r * MathUtils.cos(theta), y, r * MathUtils.sin(theta)).nor();
    }
}
