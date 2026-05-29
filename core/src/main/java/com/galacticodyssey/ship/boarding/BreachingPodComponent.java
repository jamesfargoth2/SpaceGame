package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

/**
 * A breaching pod in flight from an aggressor ship toward a target ship. It travels linearly
 * over {@link #flightDuration} seconds, then impacts — marking a hull breach and opening a
 * boarding entry point. Lives on a dedicated pod entity created by {@link
 * com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem}.
 */
public class BreachingPodComponent implements Component {
    public Entity aggressor;
    public Entity target;
    /** World-space start position. */
    public final Vector3 origin = new Vector3();
    /** World-space impact target (the target ship's hull surface estimate). */
    public final Vector3 impactPoint = new Vector3();
    /** Total flight time in seconds. */
    public float flightDuration = 1.5f;
    /** Elapsed flight time in seconds. */
    public float elapsed;
    public boolean impacted;
}
