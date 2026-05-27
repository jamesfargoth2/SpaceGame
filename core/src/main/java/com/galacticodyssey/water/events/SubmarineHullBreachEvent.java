package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

/**
 * Published when a submarine exceeds its crush depth, causing hull
 * failure. The flooding system listens for this to create breaches
 * in compartments.
 */
public final class SubmarineHullBreachEvent {

    public final Entity entity;

    /** Current depth at the moment of breach, in metres below surface. */
    public final float currentDepth;

    /** The hull's rated crush depth in metres. */
    public final float crushDepth;

    public SubmarineHullBreachEvent(Entity entity, float currentDepth, float crushDepth) {
        this.entity = entity;
        this.currentDepth = currentDepth;
        this.crushDepth = crushDepth;
    }
}
