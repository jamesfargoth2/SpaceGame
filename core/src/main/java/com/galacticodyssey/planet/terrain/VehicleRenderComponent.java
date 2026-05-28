package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;

/**
 * Holds the model asset path for a vehicle so the GL render layer can lazily build a
 * ModelInstance. Kept free of GL types so factories/systems stay headless-testable.
 */
public class VehicleRenderComponent implements Component {
    public String modelPath;
    /** Set by the render hook once the ModelInstance has been created. */
    public boolean instanceCreated;
}
