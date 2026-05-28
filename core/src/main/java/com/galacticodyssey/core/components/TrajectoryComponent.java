package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.galaxy.KeplerOrbit;

public class TrajectoryComponent implements Component, Pool.Poolable {

    public KeplerOrbit currentOrbit;
    public final Array<Vector3> predictedPath = new Array<>();
    public boolean isStable;
    public float periapsis;
    public float apoapsis;
    public boolean dirty = true;
    public int sampleSegments = 96;

    @Override
    public void reset() {
        currentOrbit = null;
        predictedPath.clear();
        isStable = false;
        periapsis = 0f;
        apoapsis = 0f;
        dirty = true;
        sampleSegments = 96;
    }
}
