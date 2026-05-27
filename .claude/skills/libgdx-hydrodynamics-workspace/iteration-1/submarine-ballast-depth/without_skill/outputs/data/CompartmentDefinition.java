package com.galacticodyssey.water.data;

import com.badlogic.gdx.utils.IntArray;

/**
 * Data-driven definition of a single watertight compartment.
 * Loaded from JSON at runtime alongside SubmarineData.
 */
public class CompartmentDefinition {

    /** Unique compartment identifier. */
    public String id = "";

    /** Display name for UI. */
    public String name = "";

    /** Volume of this compartment in cubic meters. */
    public float volume = 50f;

    /** Base breach flow rate (m^3/s) if hull breaches here. */
    public float baseBreachFlowRate = 0.5f;

    /** Bulkhead flow rate (m^3/s per unit of integrity loss). */
    public float bulkheadFlowRate = 0.3f;

    /** Offset from submarine center along forward axis (meters). */
    public float forwardOffset = 0f;

    /** Offset from submarine center along vertical axis (meters). */
    public float verticalOffset = 0f;

    /** Offset from submarine center along lateral axis (meters). */
    public float lateralOffset = 0f;

    /** Indices of connected compartments (referencing SubmarineData.compartments array). */
    public final IntArray connectedIndices = new IntArray();

    /** Initial bulkhead integrity for each connection (parallel array). */
    public float initialBulkheadIntegrity = 1f;
}
