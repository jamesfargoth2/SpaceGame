package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** Marks the capture objective on a boarded ship: the bridge, in ship-local coordinates. */
public class BridgeComponent implements Component {
    public final Vector3 localCenter = new Vector3();
    public float radius = 3f;
}
