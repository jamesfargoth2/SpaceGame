package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class DustCloud implements Pool.Poolable {
    public Vector3 origin = new Vector3();
    public float radius;
    public int particleCount;
    public float settleRate;
    public float lifetimeSeconds;
    public float elapsed;

    @Override
    public void reset() {
        origin.setZero();
        radius = 0f;
        particleCount = 0;
        settleRate = 0f;
        lifetimeSeconds = 0f;
        elapsed = 0f;
    }
}
