package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;

public class SOITrackerComponent implements Component, Pool.Poolable {

    public Entity dominantBody;
    public Entity secondaryBody;
    public float distanceToDominant;

    @Override
    public void reset() {
        dominantBody = null;
        secondaryBody = null;
        distanceToDominant = 0f;
    }
}
