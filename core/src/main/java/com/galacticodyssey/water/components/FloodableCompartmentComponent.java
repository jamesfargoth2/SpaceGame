package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * Represents a watertight compartment that can flood if the hull is breached.
 * Compartments are connected to adjacent compartments; water spreads through
 * damaged bulkheads.
 *
 * Each floodable compartment is an Ashley entity with this component.
 * The parent submarine entity references its compartments by entity ID.
 */
public class FloodableCompartmentComponent implements Component {

    /** Unique compartment identifier within the submarine. */
    public String compartmentId = "";

    /** Total volume of this compartment in cubic meters. */
    public float volume = 50f;

    /** Current water volume inside this compartment (m^3). */
    public float floodedVolume = 0f;

    /** Whether this compartment has a direct hull breach. */
    public boolean breached = false;

    /** Water ingress rate through the hull breach (m^3/s). Scales with depth pressure. */
    public float breachFlowRate = 0f;

    /** Base breach flow rate before pressure scaling (m^3/s). */
    public float baseBreachFlowRate = 0.5f;

    /** Offset from submarine center along forward axis (meters). */
    public float forwardOffset = 0f;

    /** Offset from submarine center along vertical axis (meters). */
    public float verticalOffset = 0f;

    /** Offset from submarine center along lateral axis (meters). */
    public float lateralOffset = 0f;

    /** Indices of connected compartments (into SubmarineStateComponent.compartmentEntities). */
    public final IntArray connectedCompartments = new IntArray();

    /** Bulkhead integrity for each connection (0-1). 1 = sealed, 0 = fully open.
     *  Parallel array with connectedCompartments. */
    public final Array<Float> bulkheadIntegrity = new Array<>();

    /** Flow rate through damaged bulkheads (m^3/s per unit of integrity loss). */
    public float bulkheadFlowRate = 0.3f;

    /** Returns flood fraction (0-1). */
    public float getFloodFraction() {
        return volume > 0f ? Math.min(floodedVolume / volume, 1f) : 0f;
    }

    /** Returns mass of water in this compartment (kg). */
    public float getWaterMass() {
        return floodedVolume * 1025f;
    }

    /** Whether this compartment is fully flooded. */
    public boolean isFullyFlooded() {
        return floodedVolume >= volume;
    }
}
