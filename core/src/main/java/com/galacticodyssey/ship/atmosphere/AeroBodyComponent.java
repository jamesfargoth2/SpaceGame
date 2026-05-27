package com.galacticodyssey.ship.atmosphere;

import com.badlogic.ashley.core.Component;

/**
 * Describes the aerodynamic shape of an entity that experiences lift and drag
 * when flying through an atmosphere. Attach alongside a
 * {@link com.galacticodyssey.core.components.PhysicsBodyComponent} and
 * {@link AeroStateComponent}.
 */
public class AeroBodyComponent implements Component {

    /** Reference wing area in m^2. */
    public float wingArea = 20f;

    /** Wingspan^2 / wingArea. */
    public float aspectRatio = 6f;

    /** Oswald span efficiency factor (0.7 - 0.9). */
    public float oswaldFactor = 0.8f;

    /** Zero-lift drag coefficient (0.01 - 0.05 for sleek ships). */
    public float cd0 = 0.025f;

    /** Maximum lift coefficient (1.2 - 2.0). */
    public float maxCl = 1.5f;

    /** Angle of attack at which lift drops sharply, in radians. */
    public float stallAngle = 0.30f;

    /** Angle of attack at peak Cl, in radians. */
    public float maxClAngle = 0.26f;

    /** Nose radius in metres, used for entry heating calculations. */
    public float noseRadius = 0.5f;

    /** Heat-shield frontal area in m^2. */
    public float heatShieldArea = 4f;

    /** Whether this entity is currently inside an atmosphere. */
    public boolean inAtmosphere = false;
}
