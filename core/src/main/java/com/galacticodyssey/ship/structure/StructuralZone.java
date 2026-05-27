package com.galacticodyssey.ship.structure;

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

    public StructuralZone() {}

    public StructuralZone(ZoneId id) {
        this.id = id;
    }
}
