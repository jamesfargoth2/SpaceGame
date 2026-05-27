package com.galacticodyssey.core.tether;

import com.badlogic.ashley.core.Component;

/**
 * Winch mechanic for reeling a tether in or out.
 * Must be on the same entity as a {@link TetherConstraintComponent}.
 */
public class WinchComponent implements Component {

    /** Reel rate (m/s). Positive = reel in (shorten), negative = reel out (lengthen). */
    public float reelRate;

    /** Minimum rest length the winch can shorten to (metres). */
    public float minLength = 1f;

    /** Maximum rest length the winch can extend to (metres). */
    public float maxLength = 500f;

    /** Current mechanical load on the winch motor (watts). Computed each tick. */
    public float motorLoad;
}
