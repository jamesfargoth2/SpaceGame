package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class GravitySourceComponent implements Component, Pool.Poolable {

    public float mass;
    public float minRadius = 10f;
    public float falloffExponent = 2f;
    public float influenceRadius = 50000f;
    public boolean active = true;

    @Override
    public void reset() {
        mass = 0f;
        minRadius = 10f;
        falloffExponent = 2f;
        influenceRadius = 50000f;
        active = true;
    }
}
