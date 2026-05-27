package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

/**
 * Top-level operational state for a submarine entity.
 * References child compartment entities and tracks aggregate flooding.
 */
public class SubmarineStateComponent implements Component {

    /** Operational state of the submarine. */
    public enum State {
        /** On the surface. */
        SURFACED,
        /** Diving to target depth. */
        DIVING,
        /** Maintaining target depth. */
        SUBMERGED,
        /** Rising toward surface. */
        ASCENDING,
        /** Emergency ascent (all ballast blown). */
        EMERGENCY_SURFACE,
        /** Hull compromised, flooding in progress. */
        FLOODING,
        /** Sinking uncontrollably. */
        SINKING,
        /** Resting on the seabed. */
        ON_SEABED
    }

    /** Current operational state. */
    public State state = State.SURFACED;

    /** Entities representing each compartment (each has FloodableCompartmentComponent). */
    public final Array<Entity> compartmentEntities = new Array<>();

    /** Total flood water mass across all compartments (kg). Updated by FloodingSystem. */
    public float totalFloodWaterMass = 0f;

    /** Roll angle from flooding-induced weight asymmetry (degrees). */
    public float floodInducedRoll = 0f;

    /** Pitch angle from flooding-induced weight asymmetry (degrees). */
    public float floodInducedPitch = 0f;

    /** Whether the submarine has reached an unrecoverable state. */
    public boolean criticalFailure = false;

    /** Seabed depth at current position (meters, positive = deep). */
    public float seabedDepth = 1000f;

    /** Time in seconds the submarine has been below crush depth. */
    public float timeBelowCrushDepth = 0f;
}
