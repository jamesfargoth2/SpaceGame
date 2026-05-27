package com.galacticodyssey.core.tether;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Multi-segment Verlet rope for visible cables, cargo slings, or grapple lines.
 * Position-based dynamics: velocity is implicit via (position - prevPosition).
 */
public class VerletRopeComponent implements Component {

    /** A single mass point along the rope. */
    public static class RopeSegment {
        public final Vector3 position = new Vector3();
        public final Vector3 prevPosition = new Vector3();
        public float mass = 1f;
        public boolean isFixed;

        public RopeSegment() {
        }

        public RopeSegment(Vector3 pos, float mass, boolean isFixed) {
            this.position.set(pos);
            this.prevPosition.set(pos);
            this.mass = mass;
            this.isFixed = isFixed;
        }
    }

    /** Ordered array of mass points from one end to the other. */
    public final Array<RopeSegment> segments = new Array<>();

    /** Rest distance between adjacent segments (metres). */
    public float segmentLength = 1f;

    /** Constraint correction factor per iteration (0-1). Higher = stiffer. */
    public float stiffness = 0.8f;

    /** Number of constraint-relaxation iterations per tick. */
    public int solverIterations = 6;
}
