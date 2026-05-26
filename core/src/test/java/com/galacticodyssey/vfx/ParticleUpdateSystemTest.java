package com.galacticodyssey.vfx;

import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.systems.ParticleUpdateSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleUpdateSystemTest {

    private ParticlePoolComponent pool;
    private ParticleUpdateSystem system;

    @BeforeEach
    void setUp() {
        pool = new ParticlePoolComponent();
        system = new ParticleUpdateSystem(pool);
    }

    @Test
    void particles_moveByVelocity() {
        Particle p = pool.obtain();
        p.position.set(0, 0, 0);
        p.velocity.set(10, 0, 0);
        p.life = 1f;
        p.maxLife = 1f;

        system.update(0.1f);

        assertEquals(1.0f, p.position.x, 0.01f);
        assertEquals(0.9f, p.life, 0.01f);
    }

    @Test
    void expiredParticles_returnedToPool() {
        Particle p = pool.obtain();
        p.life = 0.01f;
        p.maxLife = 1f;

        system.update(0.1f);

        assertTrue(pool.active.isEmpty());
    }

    @Test
    void particles_applyAcceleration() {
        Particle p = pool.obtain();
        p.position.set(0, 10, 0);
        p.velocity.set(0, 0, 0);
        p.acceleration.set(0, -9.8f, 0);
        p.life = 2f;
        p.maxLife = 2f;

        system.update(1.0f);

        assertEquals(-9.8f, p.velocity.y, 0.01f);
        assertEquals(0.2f, p.position.y, 0.01f);
    }
}
