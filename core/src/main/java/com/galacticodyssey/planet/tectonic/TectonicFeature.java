package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;

/** A tagged tectonic feature at a unit-sphere direction. */
public final class TectonicFeature {
    public final FeatureType type;
    public final Vector3 position; // unit vector (surface direction)

    public TectonicFeature(FeatureType type, Vector3 position) {
        this.type = type;
        this.position = position;
    }
}
