package com.galacticodyssey.core.solar;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/**
 * Marks an entity as having a deployable photon sail for solar-radiation
 * propulsion.
 */
public class PhotonSailComponent implements Component {

    /** Total sail surface area (m^2). */
    public float sailArea;

    /** Sail surface reflectivity (0-1, real sails ~ 0.85). */
    public float sailReflectivity = 0.85f;

    /** Current sail normal direction (unit vector, world space). */
    public final Vector3 sailNormal = new Vector3(0f, 0f, 1f);

    /** Whether the sail is currently deployed. */
    public boolean isSailDeployed;

    /** Tack angle applied to the sail normal for course changes (radians). */
    public float tackAngle;
}
