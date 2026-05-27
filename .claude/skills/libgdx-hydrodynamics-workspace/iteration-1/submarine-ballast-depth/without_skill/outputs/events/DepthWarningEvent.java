package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Fired when the submarine approaches or exceeds its crush depth.
 * UI systems can subscribe to show warning indicators.
 */
public final class DepthWarningEvent {

    public enum Severity {
        /** Approaching crush depth (past warning threshold). */
        WARNING,
        /** At or exceeding crush depth. */
        CRITICAL,
        /** Well past crush depth, hull actively failing. */
        CATASTROPHIC
    }

    /** The submarine entity. */
    public final Entity submarine;

    /** Warning severity level. */
    public final Severity severity;

    /** Current depth (meters). */
    public final float currentDepth;

    /** Crush depth (meters). */
    public final float crushDepth;

    /** Fraction of crush depth currently reached (can exceed 1.0). */
    public final float depthFraction;

    public DepthWarningEvent(Entity submarine, Severity severity,
                             float currentDepth, float crushDepth, float depthFraction) {
        this.submarine = submarine;
        this.severity = severity;
        this.currentDepth = currentDepth;
        this.crushDepth = crushDepth;
        this.depthFraction = depthFraction;
    }
}
