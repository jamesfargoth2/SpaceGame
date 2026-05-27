package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

/**
 * Defines a body of liquid. Attach to an entity that represents an ocean, lake,
 * or alien sea. All buoyancy, drag, and flooding systems read fluid properties
 * from this component.
 *
 * <p>Swap {@link #density} and {@link #kinematicViscosity} to simulate alien
 * fluids (methane seas, ammonia oceans, lava lakes) — the same physics math
 * works everywhere.
 */
public class WaterBodyComponent implements Component {

    /** Undisturbed surface height in local-space (Y-up). */
    public float baseHeight;

    /** Fluid density in kg/m³. Seawater = 1025, fresh = 998, methane = 450. */
    public float density = 1025f;

    /** Kinematic viscosity in m²/s. Seawater ≈ 1.19e-6. */
    public float kinematicViscosity = 1.19e-6f;

    /** Surface tension in N/m. */
    public float surfaceTension = 0.072f;

    /** Gerstner wave components composing the surface. */
    public final Array<WaveParams> waves = new Array<>(8);

    /** Ambient current velocity in local-space m/s. */
    public final Vector3 currentVelocity = new Vector3();

    /** Spatial bounds of this water body. {@code null} = infinite ocean. */
    public BoundingBox bounds;
}
