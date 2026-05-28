package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

/** Space-colonization branch growth: nodes grow toward nearby attraction points. */
public final class SpaceColonization {
    private SpaceColonization() {}

    public static class GrowthParams {
        public float influenceRadius = 4f;
        public float killDistance = 0.7f;
        public float segmentLength = 0.45f;
        public int maxNodes = 500;
    }

    public static BranchSkeleton grow(Array<Vector3> attractors, GrowthParams p, Random rng) {
        BranchSkeleton skel = new BranchSkeleton();
        skel.addNode(new Vector3(0, 0, 0), -1);

        Array<Vector3> live = new Array<>(attractors);
        int safety = p.maxNodes * 4;

        while (live.size > 0 && skel.size() < p.maxNodes && safety-- > 0) {
            // Accumulate a growth direction per node from its influencing attractors.
            Vector3[] dir = new Vector3[skel.size()];
            int[] hits = new int[skel.size()];
            float inf2 = p.influenceRadius * p.influenceRadius;

            for (Vector3 a : live) {
                int nearest = -1; float best = Float.MAX_VALUE;
                for (int n = 0; n < skel.size(); n++) {
                    float d2 = skel.position(n).dst2(a);
                    if (d2 < best) { best = d2; nearest = n; }
                }
                if (nearest >= 0 && best <= inf2) {
                    if (dir[nearest] == null) dir[nearest] = new Vector3();
                    dir[nearest].add(new Vector3(a).sub(skel.position(nearest)).nor());
                    hits[nearest]++;
                }
            }

            boolean grew = false;
            int existing = skel.size();
            for (int n = 0; n < existing; n++) {
                if (hits[n] == 0) continue;
                Vector3 d = dir[n].nor();
                if (d.isZero(1e-5f)) continue;
                Vector3 np = new Vector3(skel.position(n)).mulAdd(d, p.segmentLength);
                skel.addNode(np, n);
                grew = true;
                if (skel.size() >= p.maxNodes) break;
            }
            if (!grew) break;

            // Remove attractors that any node has now reached.
            float kill2 = p.killDistance * p.killDistance;
            for (int i = live.size - 1; i >= 0; i--) {
                Vector3 a = live.get(i);
                for (int n = 0; n < skel.size(); n++) {
                    if (skel.position(n).dst2(a) <= kill2) { live.removeIndex(i); break; }
                }
            }
        }

        assignRadii(skel);
        return skel;
    }

    /** Relative radius from descendant count: thick at the root (1.0), thin at tips. */
    private static void assignRadii(BranchSkeleton skel) {
        int n = skel.size();
        int[] descendants = new int[n];
        boolean[] hasChild = new boolean[n];
        // children always have a higher index than their parent, so iterate in reverse.
        for (int i = n - 1; i >= 1; i--) {
            int p = skel.parent(i);
            descendants[p] += descendants[i] + 1;
            hasChild[p] = true;
        }
        float rootD = descendants[0] + 1f;
        float[] rel = new float[n];
        boolean[] tip = new boolean[n];
        for (int i = 0; i < n; i++) {
            float frac = (descendants[i] + 1f) / rootD;            // 0..1
            rel[i] = Math.max(0.08f, (float) Math.sqrt(frac));      // sqrt keeps mid-branches visible
            tip[i] = !hasChild[i];
        }
        rel[0] = 1f;
        skel.finalizeRadii(rel, tip);
    }
}
