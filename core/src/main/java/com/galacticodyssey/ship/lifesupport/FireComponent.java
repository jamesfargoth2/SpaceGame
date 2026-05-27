package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Tracks an active fire in a compartment.
 * Entities with this component must also have a CompartmentAtmosphereComponent.
 */
public class FireComponent implements Component, Pool.Poolable {

    /** Fire intensity (0 = extinguished, 1 = normal, higher = raging). */
    public float intensity = 1.0f;

    /** Base O2 consumption rate in kg/s at intensity 1.0. */
    public float burnRate = 0.01f;

    /** Heat output in K/(m3*s) at intensity 1.0. */
    public float heatOutput = 5.0f;

    @Override
    public void reset() {
        intensity = 1.0f;
        burnRate = 0.01f;
        heatOutput = 5.0f;
    }
}
