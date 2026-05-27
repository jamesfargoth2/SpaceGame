package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Tracks flooding state across a vessel's interior compartments. The
 * {@link com.galacticodyssey.water.systems.FloodingSystem} reads breach areas
 * and depths to compute water ingress and cross-compartment flow.
 */
public class FloodingComponent implements Component {

    /** All compartments in this vessel's interior. */
    public final Array<Compartment> compartments = new Array<>();

    /** Total mass of flood water across all compartments in kg. */
    public float totalFloodedMass;

    /** Centre-of-mass shift caused by flood water in hull body frame. */
    public final Vector3 floodedCoM = new Vector3();
}
