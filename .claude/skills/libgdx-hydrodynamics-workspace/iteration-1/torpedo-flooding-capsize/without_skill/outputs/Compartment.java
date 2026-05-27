package com.galacticodyssey.ship.flooding;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Represents a single watertight compartment within a ship's hull.
 *
 * <p>Each compartment tracks its total volume, current water volume, and any
 * hull breach through which external fluid enters. Connected compartments
 * exchange water through doorways modelled by {@link DoorwayConnection}.
 *
 * <p>All positions are in the ship's interior body frame (local to the
 * ship's interior {@code btDynamicsWorld}, per CLAUDE.md rule 6).
 */
public final class Compartment {

    /** Human-readable identifier (e.g. "engine_room", "cargo_hold"). */
    public final String id;

    /** Total air volume of this compartment in cubic metres. */
    public final float volume;

    /** Current volume of water inside this compartment (m^3). */
    public float waterVolume;

    /**
     * Area of the hull breach opening in m^2. Zero means the hull is intact
     * in this compartment and no external ingress occurs.
     */
    public float breachArea;

    /**
     * Depth of the breach below the external waterline in metres. Only
     * meaningful when {@link #breachArea} > 0. Drives Torricelli's theorem
     * flow rate calculation.
     */
    public float breachDepth;

    /** Centre of this compartment in the ship's interior body frame. */
    public final Vector3 centroid = new Vector3();

    /** IDs of compartments this one is connected to via doorways/passages. */
    public final Array<String> connectedTo = new Array<>();

    /**
     * Whether the player (or an automated system) has sealed this
     * compartment's breach. When sealed, {@link #breachArea} is zeroed
     * and no further external ingress occurs.
     */
    public boolean sealed;

    public Compartment(String id, float volume) {
        this.id = id;
        this.volume = volume;
    }

    /** Fraction of compartment that is flooded, clamped to [0, 1]. */
    public float fillFraction() {
        if (volume <= 0f) return 0f;
        return Math.min(waterVolume / volume, 1f);
    }

    /**
     * Effective water head height assuming the water fills the compartment
     * uniformly from the bottom. Uses centroid.y as the floor reference and
     * the compartment's geometric height derived from volume and a
     * representative cross-section area.
     */
    public float waterHead(float compartmentHeight) {
        if (waterVolume <= 0f || volume <= 0f) return 0f;
        return fillFraction() * compartmentHeight;
    }
}
