package com.galacticodyssey.water;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * A single watertight compartment inside a vessel's hull. Tracks water
 * ingress volume, breach dimensions, and connectivity to adjacent
 * compartments for cross-flow equalization.
 */
public final class Compartment {

    /** Unique identifier for this compartment. */
    public String id;

    /** Total volume of the compartment in m³. */
    public float volume;

    /** Current volume of flood water inside in m³. */
    public float waterVolume;

    /** Hull breach area in m². 0 = hull intact. */
    public float breachArea;

    /** Depth of the breach below the waterline in metres. */
    public float breachDepth;

    /** IDs of compartments connected to this one (passageways, hatches). */
    public final Array<String> connectedTo = new Array<>();

    /** Centre of this compartment in hull body frame. */
    public final Vector3 centroid = new Vector3();

    /**
     * Returns the fill fraction of this compartment: 0 = empty, 1 = full.
     */
    public float fillFraction() {
        if (volume <= 0f) return 0f;
        return waterVolume / volume;
    }
}
