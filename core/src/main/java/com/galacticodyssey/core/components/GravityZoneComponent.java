package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class GravityZoneComponent implements Component, Pool.Poolable {

    public enum Mode { ZERO_G, CONSTANT, SCALE, REDIRECT }

    public Mode mode = Mode.ZERO_G;
    public final Vector3 constantVector = new Vector3();
    public float scaleFactor = 1f;
    public float radius = 100f;

    @Override
    public void reset() {
        mode = Mode.ZERO_G;
        constantVector.setZero();
        scaleFactor = 1f;
        radius = 100f;
    }
}
