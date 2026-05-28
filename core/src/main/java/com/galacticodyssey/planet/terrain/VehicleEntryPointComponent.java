package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** Walk-in trigger for entering the driver seat, plus where the player is dropped on exit. */
public class VehicleEntryPointComponent implements Component {
    public float triggerRadius = 2.5f;
    /** Where the player appears (vehicle-local) when exiting the vehicle. */
    public final Vector3 localExitOffset = new Vector3(2f, 0f, 0f);
}
