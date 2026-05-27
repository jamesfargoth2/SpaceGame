package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;

/**
 * Tracks the net buoyancy state of an underwater entity.
 * Buoyancy force = (water density) * (submerged volume) * g  minus  (total mass * g).
 * Positive net buoyancy means the entity floats; negative means it sinks.
 */
public class BuoyancyComponent implements Component {

    /** Water density in kg/m^3 (seawater default). */
    public float waterDensity = 1025f;

    /** Gravitational acceleration in m/s^2. */
    public float gravity = 9.81f;

    /** Fraction of the hull volume currently submerged (0-1). */
    public float submergedFraction = 0f;

    /** Net buoyancy force in Newtons (positive = upward). */
    public float netBuoyancyForce = 0f;

    /** Surface level Y coordinate in local space. */
    public float surfaceLevel = 0f;

    /** Whether the entity is currently fully submerged. */
    public boolean fullySubmerged = false;

    /** Whether the entity is at the surface (partially submerged). */
    public boolean atSurface = false;

    /** Current effective mass including ballast water and flooding (kg). */
    public float effectiveMass = 0f;

    /** Center of buoyancy offset from entity origin along Y (meters). */
    public float centerOfBuoyancyY = 0f;

    /** Center of gravity offset from entity origin along Y (meters). */
    public float centerOfGravityY = 0f;

    /** Metacentric height: distance between center of gravity and metacenter (meters).
     *  Positive = stable, negative = will capsize. */
    public float metacentricHeight = 1.5f;
}
