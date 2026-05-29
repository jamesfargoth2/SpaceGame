package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SpaceColonizationTest {
    private static SpaceColonization.GrowthParams params() {
        SpaceColonization.GrowthParams p = new SpaceColonization.GrowthParams();
        p.influenceRadius = 4f; p.killDistance = 0.7f; p.segmentLength = 0.45f; p.maxNodes = 400;
        return p;
    }

    private static BranchSkeleton grow(long seed) {
        Random rng = new Random(seed);
        Array<Vector3> pts = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 10f, 3f, 160, rng);
        return SpaceColonization.grow(pts, params(), new Random(seed));
    }

    @Test
    void producesConnectedBoundedSkeleton() {
        BranchSkeleton s = grow(1);
        assertTrue(s.size() >= 2, "should grow past the root");
        assertTrue(s.size() <= params().maxNodes, "must respect maxNodes");
        assertEquals(-1, s.parent(0), "node 0 is the root");
        for (int i = 1; i < s.size(); i++) {
            int p = s.parent(i);
            assertTrue(p >= 0 && p < i, "parent index must precede child: node " + i + " -> " + p);
        }
    }

    @Test
    void rootIsThickestTipsAreThin() {
        BranchSkeleton s = grow(2);
        assertEquals(1f, s.relRadius(0), 1e-4f, "root has full relative radius");
        for (int i = 0; i < s.size(); i++) {
            assertTrue(s.relRadius(i) > 0f && s.relRadius(i) <= 1f);
            if (s.isTip(i)) assertTrue(s.relRadius(i) < 1f, "a tip should be thinner than the root");
        }
    }

    @Test
    void isDeterministic() {
        BranchSkeleton a = grow(123), b = grow(123);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.position(i), b.position(i));
            assertEquals(a.parent(i), b.parent(i));
        }
    }
}
