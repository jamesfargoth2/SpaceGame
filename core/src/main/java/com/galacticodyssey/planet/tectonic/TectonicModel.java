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

    // Filled in Task 5.
    private float continentalFraction;
    private final List<TectonicFeature> features = new ArrayList<>();

    // Scratch vectors (single-threaded generation/query).
    private final Vector3 va = new Vector3();
    private final Vector3 vb = new Vector3();
    private final Vector3 n = new Vector3();
    private final Vector3 rel = new Vector3();

    public TectonicModel(List<Plate> plates, List<Vector3> hotspots, TectonicConfig config) {
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

        // Tangential boundary normal pointing from A's center toward B's center.
        n.set(b.center).sub(a.center);
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
                boolean aContinental = !a.oceanic && a.baseElevation >= config.continentalBase;
                boolean bContinental = !b.oceanic && b.baseElevation >= config.continentalBase;
                type = (aContinental && bContinental)
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

    // --- baked in Task 5 ---
    private void bakeContinentalFraction() { /* Task 5 */ }
    private void bakeFeatures() { /* Task 5 */ }

    List<Plate> plates() { return plates; }
    TectonicConfig config() { return config; }
}
