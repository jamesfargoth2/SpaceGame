package com.galacticodyssey.water;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * A single watertight compartment inside a vessel's hull. Tracks water
 * ingress volume, breach dimensions, and connectivity to adjacent
 * compartments for cross-flow equalization.
 */
public final class Compartment {

    public String id;
    public float volume;
    public float waterVolume;
    public float breachArea;
    public float breachDepth;
    public final Array<String> connectedTo = new Array<>();
    public final Vector3 centroid = new Vector3();
    public boolean sealed;

    public Compartment(String id, float volume) {
        this.id = id;
        this.volume = volume;
    }

    public Compartment() {}

    public float fillFraction() {
        if (volume <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, waterVolume / volume));
    }

    public float waterHead(float compartmentHeight) {
        return fillFraction() * compartmentHeight;
    }
}
