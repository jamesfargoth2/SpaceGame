package com.galacticodyssey.ship.flooding.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when the ship's stability degrades past a warning threshold
 * due to flooding. The free-surface effect of partially flooded
 * compartments reduces the effective metacentric height, making the
 * ship increasingly vulnerable to capsizing.
 *
 * <p>The HUD subscribes to this event to display stability warnings
 * with escalating severity.
 */
public final class StabilityWarningEvent {

    /** The ship entity experiencing stability loss. */
    public final Entity shipEntity;

    /** Free-surface GZ loss in metres. Higher values mean less stability. */
    public final float gzLoss;

    /** Current roll angle in degrees. */
    public final float rollDeg;

    /**
     * Severity level: 0 = caution, 1 = warning, 2 = critical.
     * Determined by thresholds on GZ loss and roll angle.
     */
    public final int severity;

    public StabilityWarningEvent(Entity shipEntity, float gzLoss, float rollDeg, int severity) {
        this.shipEntity = shipEntity;
        this.gzLoss = gzLoss;
        this.rollDeg = rollDeg;
        this.severity = severity;
    }
}
