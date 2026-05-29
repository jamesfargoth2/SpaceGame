package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlateTest {

    @Test
    void velocityIsTangentToSphere() {
        // Pole along +Y, query point on the equator (+X): velocity should be tangent (dot with p ~ 0).
        Plate plate = new Plate(0, new Vector3(1, 0, 0), true, -0.3f,
                new Vector3(0, 1, 0), 0.5f);
        Vector3 p = new Vector3(1, 0, 0).nor();
        Vector3 v = plate.velocityAt(p, new Vector3());
        assertEquals(0f, v.dot(p), 1e-5f, "surface velocity must be tangent to the sphere");
        assertTrue(v.len() > 0f, "velocity should be non-zero away from the pole");
    }

    @Test
    void velocityZeroAtPole() {
        Plate plate = new Plate(0, new Vector3(0, 1, 0), false, 0.3f,
                new Vector3(0, 1, 0), 0.5f);
        Vector3 pole = new Vector3(0, 1, 0).nor();
        Vector3 v = plate.velocityAt(pole, new Vector3());
        assertEquals(0f, v.len(), 1e-5f, "velocity at the Euler pole is zero");
    }
}
