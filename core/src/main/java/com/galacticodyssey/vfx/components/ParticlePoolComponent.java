package com.galacticodyssey.vfx.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.vfx.Particle;
import java.util.ArrayList;
import java.util.List;

public class ParticlePoolComponent implements Component {
    public static final int MAX_PARTICLES = 4096;

    private final Pool<Particle> pool = new Pool<Particle>(256, MAX_PARTICLES) {
        @Override
        protected Particle newObject() {
            return new Particle();
        }
    };

    public final List<Particle> active = new ArrayList<>(MAX_PARTICLES);

    public Particle obtain() {
        if (active.size() >= MAX_PARTICLES) {
            Particle oldest = active.remove(0);
            pool.free(oldest);
        }
        Particle p = pool.obtain();
        active.add(p);
        return p;
    }

    public void free(Particle p) {
        active.remove(p);
        pool.free(p);
    }

    public void freeAll() {
        for (Particle p : active) {
            pool.free(p);
        }
        active.clear();
    }
}
