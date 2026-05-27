package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class GravityAffectedComponent implements Component, Pool.Poolable {

    public float mass = 1f;
    public final Vector3 lastAcceleration = new Vector3();

    @Override
    public void reset() {
        mass = 1f;
        lastAcceleration.setZero();
    }
}
