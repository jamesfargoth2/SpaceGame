package com.galacticodyssey.ship.structure;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Data class representing a single structural zone of a ship hull.
 * Not an ECS component -- stored inside {@link StructuralIntegrityComponent}.
 */
public class StructuralZone {

    /** Which zone this is. */
    public final ZoneId id;

    /** Structural health, 0 = total failure, 1 = pristine. */
    public float integrity = 1f;

    /** Yield stress before damage begins (Pa). */
    public float maxStress = 250e6f;

    /** Stress accumulated this tick (Pa). */
    public float currentStress;

    /** Interior atmospheric pressure (Pa). */
    public float pressure = 101325f;

    /** Whether the hull is breached and venting atmosphere. */
    public boolean isBreached;

    /** Zones structurally adjacent for cascade propagation. */
    public final Array<ZoneId> adjacentZones = new Array<>();

    /** Structural cross-section area (m^2). */
    public float area = 10f;

    /** Hull / armour thickness (m). */
    public float thickness = 0.02f;

    /** Whether this zone forms part of the exterior pressure boundary. */
    public boolean isBoundaryZone = true;

    /** Hull radius at this zone for hoop-stress calculation (m). */
    public float hullRadius = 3f;

    /** Mass this zone contributes to structural loading (kg). */
    public float massContribution = 500f;

    /** Interior volume of this zone (m^3). */
    public float volume = 50f;

    /** Area of the breach opening (m^2). Zero when intact. */
    public float breachArea;

    /** Outward-facing normal of the breach opening in local space. */
    public final Vector3 breachNormal = new Vector3();

    public StructuralZone(ZoneId id) {
        this.id = id;
    }
}
