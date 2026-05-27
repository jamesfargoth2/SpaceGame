package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Defines a docking port on a ship entity. The port has a local-space position
 * and outward-facing normal that are transformed into world space via the
 * owning entity's {@link TransformComponent}.
 */
public class DockingPortComponent implements Component {

    /** Position of the port in entity-local space. */
    public final Vector3 localPosition = new Vector3();

    /** Outward-facing normal of the port in entity-local space (unit vector). */
    public final Vector3 localNormal = new Vector3(0f, 0f, 1f);

    /** Maximum distance at which capture can be attempted (metres). */
    public float captureRadius = 1.5f;

    /** Maximum closing speed for a successful soft capture (m/s). */
    public float maxCaptureSpeed = 0.3f;

    /** Maximum roll misalignment between ports that still allows latching (degrees). */
    public float maxRollMismatchDeg = 15f;

    /** Whether the capture latches are currently engaged. */
    public boolean isLatched;

    /** The entity whose port is connected to this one, or {@code null} if unconnected. */
    public Entity connectedPort;

    /** Unique identifier for this port within its owning entity. */
    public String portId = "";

    // -- scratch vectors (avoid per-frame allocation) --
    private static final Vector3 tmpVec = new Vector3();
    private static final Quaternion tmpQuat = new Quaternion();

    /**
     * Returns the world-space position of this port, computed from the entity's transform.
     * The result is written into the supplied {@code out} vector and also returned for chaining.
     */
    public Vector3 worldPosition(TransformComponent transform, Vector3 out) {
        return out.set(localPosition).mul(transform.rotation).add(transform.position);
    }

    /**
     * Returns the world-space normal of this port, computed from the entity's transform.
     * The result is written into the supplied {@code out} vector and also returned for chaining.
     */
    public Vector3 worldNormal(TransformComponent transform, Vector3 out) {
        return out.set(localNormal).mul(transform.rotation).nor();
    }
}
