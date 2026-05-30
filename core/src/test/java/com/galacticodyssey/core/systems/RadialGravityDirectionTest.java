package com.galacticodyssey.core.systems;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadialGravityDirectionTest {
    @Test
    void forcePointsFromBodyTowardPlanetCentre() {
        RadialGravitySystem sys = new RadialGravitySystem(null, new Vector3(0, -6_371_000f, 0), 9.81f);
        Vector3 out = new Vector3();
        sys.computeGravityForce(new Vector3(0, 2f, 0), 80f, out);
        assertEquals(0f, out.x, 1e-2f);
        assertEquals(-9.81f * 80f, out.y, 1e-1f);
        assertEquals(0f, out.z, 1e-2f);
    }

    @Test
    void rebaseKeepsForceDirectionStable() {
        RadialGravitySystem sys = new RadialGravitySystem(null, new Vector3(0, -6_371_000f, 0), 9.81f);
        sys.onOriginRebased(800f, 0f, 0f);
        Vector3 out = new Vector3();
        sys.computeGravityForce(new Vector3(0, 2f, 0), 80f, out);
        assertEquals(-9.81f * 80f, out.y, 1f);
        assertTrue(Math.abs(out.x) < 2f, "tiny eastward tilt expected, got " + out.x);
    }
}
