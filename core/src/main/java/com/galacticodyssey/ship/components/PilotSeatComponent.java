package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class PilotSeatComponent implements Component {
    public final Vector3 interiorPosition = new Vector3();
    public float triggerRadius = 2f;
    public boolean occupied;
    public Entity occupant;
}
