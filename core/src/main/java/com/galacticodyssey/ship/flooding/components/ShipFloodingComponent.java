package com.galacticodyssey.ship.flooding.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.water.Compartment;
import com.galacticodyssey.ship.flooding.DoorwayConnection;

/**
 * Ashley ECS component that tracks the flooding state of a ship's interior.
 *
 * <p>Attached to the ship entity alongside
 * {@link com.galacticodyssey.ship.components.ShipInteriorComponent}. The
 * {@link com.galacticodyssey.ship.flooding.systems.ShipFloodingSystem} reads
 * and updates this component each tick.
 *
 * <p>All positions (compartment centroids, floodedCoM) are in the ship's
 * interior body frame. The flooding system converts mass/CoM effects
 * to the parent hull's rigid body in the outer world.
 */
public class ShipFloodingComponent implements Component {

    /** All compartments in this ship that can flood. */
    public final Array<Compartment> compartments = new Array<>();

    /** All doorway/passage connections between compartments. */
    public final Array<DoorwayConnection> doorways = new Array<>();

    /** Total mass of water inside the ship (kg). Updated each tick. */
    public float totalFloodedMass;

    /** Centre-of-mass shift due to flooding, in ship body frame. */
    public final Vector3 floodedCoM = new Vector3();

    /**
     * Density of the flooding fluid in kg/m^3. Defaults to seawater
     * (1025 kg/m^3) but can be overridden for alien environments
     * per the skill's fluid property presets.
     */
    public float fluidDensity = 1025f;

    /**
     * Height of each compartment in metres. Used for water head
     * calculations. Uniform for simplicity; a more detailed model
     * could store per-compartment heights.
     */
    public float compartmentHeight = 3.0f;

    /**
     * Gravity magnitude in m/s^2 for Torricelli flow calculations.
     * Defaults to Earth standard; override for low-g environments.
     */
    public float gravity = 9.81f;

    /**
     * Roll angle in degrees. Updated by the flooding system from the
     * ship's rigid body orientation. Used by the HUD for stability
     * warnings and capsize detection.
     */
    public float currentRollDeg;

    /**
     * Pitch angle in degrees. Updated similarly to roll.
     */
    public float currentPitchDeg;

    /**
     * Free surface GZ (metacentric height) loss in metres. Partially
     * filled compartments let water slosh to the low side, reducing
     * stability. This is the dominant cause of capsizing in damaged
     * ships.
     */
    public float freeSurfaceGzLoss;

    /**
     * Whether the ship has capsized (roll > 60 deg and not recovering).
     */
    public boolean capsized;

    /**
     * Time accumulator for capsize detection. The ship must exceed the
     * capsize angle for a sustained duration before we declare capsize.
     */
    public float capsizeTimer;
}
