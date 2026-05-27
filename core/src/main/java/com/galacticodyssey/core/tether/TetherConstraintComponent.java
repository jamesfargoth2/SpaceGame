package com.galacticodyssey.core.tether;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

/**
 * Single-segment tether constraint between two entities.
 * Supports elastic (spring) and inelastic (rigid cable) modes.
 * Attach to the entity that "owns" the tether; anchorA/anchorB reference the connected endpoints.
 */
public class TetherConstraintComponent implements Component {

    /** Entity at the A-end of the tether. */
    public Entity anchorA;

    /** Entity at the B-end of the tether. */
    public Entity anchorB;

    /** Attachment point in anchorA's local space. */
    public final Vector3 localOffsetA = new Vector3();

    /** Attachment point in anchorB's local space. */
    public final Vector3 localOffsetB = new Vector3();

    /** Rest length of the tether in metres. */
    public float restLength;

    /** Current distance between world-space anchor points (computed each tick). */
    public float currentLength;

    /** Maximum length before the cable goes taut. If equal to restLength the cable is always taut. */
    public float maxLength;

    /** Tension (N) at which the tether snaps. */
    public float breakTension = Float.MAX_VALUE;

    /** Spring constant (N/m). 0 = inelastic cable, >0 = elastic/bungee. */
    public float springConstant;

    /** Velocity damping along the tether axis (N*s/m). */
    public float damping;

    /** Whether this tether has been broken. Once true the system will skip it. */
    public boolean isBroken;

    /** Whether the cable is currently slack (distance < maxLength with no spring). */
    public boolean isSlack;

    /** Last computed tension magnitude (N). */
    public float currentTension;
}
