package com.galacticodyssey.ship.docking;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Clohessy-Wiltshire (CW) relative motion equations for two objects in
 * nearly the same circular orbit.
 * <p>
 * Axes follow the LVLH (Local Vertical Local Horizontal) frame of the target:
 * <ul>
 *   <li>x -- radial (toward/away from the central body)</li>
 *   <li>y -- along-track (prograde/retrograde)</li>
 *   <li>z -- cross-track (normal to the orbital plane)</li>
 * </ul>
 * <p>
 * This is a pure utility class with no ECS dependency; it can be called by
 * autopilot logic or prediction HUD overlays.
 */
public final class CWRelativeMotion {

    private CWRelativeMotion() {
    }

    /**
     * Propagate relative position and velocity over a time step using the
     * linearised CW solution.
     *
     * @param relPos in/out -- relative position in LVLH frame (modified in place)
     * @param relVel in/out -- relative velocity in LVLH frame (modified in place)
     * @param n      mean orbital angular velocity of the target orbit (rad/s)
     * @param dt     time step (seconds)
     */
    public static void propagate(Vector3 relPos, Vector3 relVel, float n, float dt) {
        float x0 = relPos.x;
        float y0 = relPos.y;
        float z0 = relPos.z;
        float dx0 = relVel.x;
        float dy0 = relVel.y;
        float dz0 = relVel.z;

        float nt = n * dt;
        float s = MathUtils.sin(nt);
        float c = MathUtils.cos(nt);

        // Position propagation
        relPos.x = (4f - 3f * c) * x0 + s * dx0 / n + 2f * (1f - c) * dy0 / n;
        relPos.y = 6f * (s - nt) * x0 + y0
                   + (2f * (c - 1f) * dx0 + (4f * s - 3f * nt) * dy0) / n;
        relPos.z = z0 * c + dz0 * s / n;

        // Velocity propagation
        relVel.x = 3f * n * s * x0 + c * dx0 + 2f * s * dy0;
        relVel.y = 6f * n * (c - 1f) * x0 - 2f * s * dx0 + (4f * c - 3f) * dy0;
        relVel.z = -z0 * n * s + dz0 * c;
    }

    /**
     * Compute the mean orbital angular velocity from the gravitational parameter
     * and the semi-major axis of the orbit.
     *
     * @param GM            gravitational parameter of the central body (m^3/s^2)
     * @param semiMajorAxis semi-major axis of the orbit (metres)
     * @return mean motion n (rad/s)
     */
    public static float meanMotion(float GM, float semiMajorAxis) {
        float a3 = semiMajorAxis * semiMajorAxis * semiMajorAxis;
        return (float) Math.sqrt(GM / a3);
    }
}
