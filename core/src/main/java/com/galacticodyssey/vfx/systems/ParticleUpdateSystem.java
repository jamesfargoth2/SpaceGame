package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;

import java.util.ArrayList;
import java.util.List;

public class ParticleUpdateSystem extends EntitySystem {
    private static final int PRIORITY = 13;
    private final ParticlePoolComponent pool;
    private final List<Particle> toRemove = new ArrayList<>();

    public ParticleUpdateSystem(ParticlePoolComponent pool) {
        super(PRIORITY);
        this.pool = pool;
    }

    @Override
    public void update(float deltaTime) {
        toRemove.clear();
        for (Particle p : pool.active) {
            p.velocity.mulAdd(p.acceleration, deltaTime);
            p.position.mulAdd(p.velocity, deltaTime);
            p.rotation += p.angularVelocity * deltaTime;
            p.life -= deltaTime;
            if (p.life <= 0) {
                toRemove.add(p);
            }
        }
        for (Particle p : toRemove) {
            pool.free(p);
        }
    }
}
