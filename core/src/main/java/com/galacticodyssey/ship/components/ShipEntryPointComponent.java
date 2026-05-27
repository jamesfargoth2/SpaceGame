package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class ShipEntryPointComponent implements Component {
    public final Vector3 worldPosition = new Vector3();
    public final Vector3 interiorPosition = new Vector3();
    public final Vector3 localExteriorPosition = new Vector3();
    public float triggerRadius = 5f;
    public boolean rampDeployed = true;
}
