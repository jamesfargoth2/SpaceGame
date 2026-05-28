package com.galacticodyssey.vfx;

import com.badlogic.gdx.utils.Pool;
import java.util.ArrayList;
import java.util.List;

public final class MeshParticlePool {

    private static final int MAX = 512;

    private final Pool<MeshParticle> pool = new Pool<MeshParticle>(64, MAX) {
        @Override
        protected MeshParticle newObject() {
            return new MeshParticle();
        }
    };

    public final List<MeshParticle> active = new ArrayList<>(MAX);

    public MeshParticle obtain() {
        if (active.size() >= MAX) {
            MeshParticle oldest = active.remove(0);
            pool.free(oldest);
        }
        MeshParticle mp = pool.obtain();
        active.add(mp);
        return mp;
    }

    public void free(MeshParticle mp) {
        active.remove(mp);
        pool.free(mp);
    }

    public void freeAll() {
        for (MeshParticle mp : active) pool.free(mp);
        active.clear();
    }
}
