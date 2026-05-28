package com.galacticodyssey.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshParticleTest {

    @Test
    void gravityAccumulatesVelocity() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(0, 100f, 0); // high up — won't hit floor
        mp.velocity.set(0, 0, 0);
        mp.gravity = -10f;
        mp.bounce = 0f;
        mp.life = 1f;
        mp.update(1f);
        assertEquals(-10f, mp.velocity.y, 0.001f);
    }

    @Test
    void positionAdvancesWithVelocity() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(0, 10f, 0);
        mp.velocity.set(2f, 0f, 3f);
        mp.gravity = 0f;
        mp.bounce = 0f;
        mp.life = 1f;
        mp.update(0.5f);
        assertEquals(1f, mp.position.x, 0.001f);
        assertEquals(10f, mp.position.y, 0.001f);
        assertEquals(1.5f, mp.position.z, 0.001f);
    }

    @Test
    void bounceInvertsYVelocityAtFloor() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(0, 0.01f, 0); // just above floor
        mp.velocity.set(0, -5f, 0);
        mp.gravity = 0f;
        mp.bounce = 0.6f;
        mp.life = 1f;
        mp.update(0.1f);
        assertTrue(mp.velocity.y > 0, "Expected upward bounce velocity");
        assertTrue(mp.position.y >= 0f, "Expected particle at or above floor");
    }

    @Test
    void resetClearsAllFields() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(1, 2, 3);
        mp.velocity.set(4, 5, 6);
        mp.life = 0.5f;
        mp.meshId = "spark_line";
        mp.reset();
        assertEquals(0f, mp.position.x);
        assertEquals(0f, mp.position.y);
        assertEquals(0f, mp.position.z);
        assertEquals(0f, mp.life);
        assertNull(mp.meshId);
    }
}
