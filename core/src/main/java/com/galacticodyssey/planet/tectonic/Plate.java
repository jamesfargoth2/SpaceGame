package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;

/** One rigid tectonic plate: a region anchored at a center direction with a rigid rotation. */
public final class Plate {
    public final int id;
    public final Vector3 center;       // unit vector
    public final boolean oceanic;
    public final float baseElevation;  // normalized; continental >= 0, oceanic < 0
    public final Vector3 eulerPole;    // unit vector (rotation axis)
    public final float angularSpeed;   // arbitrary units; only relative motion matters

    public Plate(int id, Vector3 center, boolean oceanic, float baseElevation,
                 Vector3 eulerPole, float angularSpeed) {
        this.id = id;
        this.center = center.cpy().nor();
        this.oceanic = oceanic;
        this.baseElevation = baseElevation;
        this.eulerPole = eulerPole.cpy().nor();
        this.angularSpeed = angularSpeed;
    }

    /** Surface velocity at unit-direction p: v = (angularSpeed * pole) x p. Writes into out, returns out. */
    public Vector3 velocityAt(Vector3 p, Vector3 out) {
        out.set(eulerPole).crs(p).scl(angularSpeed);
        return out;
    }
}
