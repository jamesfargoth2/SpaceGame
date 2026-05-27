package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;

/**
 * Hydrodynamic drag properties for an underwater entity.
 * Drag force = 0.5 * waterDensity * v^2 * Cd * A
 * where Cd is the drag coefficient and A is the reference area.
 */
public class WaterDragComponent implements Component {

    /** Drag coefficient along the forward axis (streamlined direction). */
    public float forwardDragCoefficient = 0.04f;

    /** Drag coefficient along the lateral axis. */
    public float lateralDragCoefficient = 1.2f;

    /** Drag coefficient along the vertical axis. */
    public float verticalDragCoefficient = 0.8f;

    /** Forward reference area in m^2. */
    public float forwardReferenceArea = 20f;

    /** Lateral reference area in m^2 (length * height). */
    public float lateralReferenceArea = 125f;

    /** Vertical reference area in m^2 (length * beam). */
    public float verticalReferenceArea = 150f;

    /** Angular (rotational) drag coefficient. */
    public float angularDragCoefficient = 5.0f;

    /** Added mass coefficient (accounts for water entrained in motion, typically 0.1 - 0.5). */
    public float addedMassCoefficient = 0.2f;
}
