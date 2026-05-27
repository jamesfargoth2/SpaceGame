package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

/**
 * Structural constraint that locks two docked ships together at their
 * docking ports, preventing relative motion at the connection point.
 */
public class HardDockConstraintComponent implements Component {

    /** First ship entity in the docked pair. */
    public Entity shipA;

    /** Second ship entity in the docked pair. */
    public Entity shipB;

    /** Attachment point in shipA's local space (its docking port position). */
    public final Vector3 localOffsetA = new Vector3();

    /** Attachment point in shipB's local space (its docking port position). */
    public final Vector3 localOffsetB = new Vector3();

    /** Whether this constraint is currently being enforced. */
    public boolean isActive;
}
