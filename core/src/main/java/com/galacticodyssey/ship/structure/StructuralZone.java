package com.galacticodyssey.ship.structure;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Represents a single structural zone of a ship, tracking integrity and
 * pressurization state.
 */
public class StructuralZone {
    public ZoneId id;
    public float integrity = 1.0f;
    public float pressure = 1.0f;
    public boolean isBreached;
    public float breachArea;

    public float volume;
    public final Vector3 breachNormal = new Vector3();
    public final Array<ZoneId> adjacentZones = new Array<>();

    public float currentStress;
    public float area;
    public float massContribution;
    public boolean isBoundaryZone;
    public float thickness;
    public float hullRadius;
    public float maxStress = 1e6f;

    public StructuralZone() {}

    public StructuralZone(ZoneId id) {
        this.id = id;
    }
}
