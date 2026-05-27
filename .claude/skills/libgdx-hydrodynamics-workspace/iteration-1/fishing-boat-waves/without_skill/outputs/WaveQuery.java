package com.galacticodyssey.water;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Stateless utility that evaluates the Gerstner wave model defined by a
 * {@link WaterBodyComponent} at an arbitrary position and time.
 *
 * <p>All methods are pure functions with no heap allocations (callers supply
 * pre-allocated output objects). Coordinates are in <strong>galaxy-space</strong>
 * (double) so waves tile seamlessly across floating-origin rebases.
 */
public final class WaveQuery {

    private WaveQuery() {}

    /**
     * Computes the water surface height (Y) at galaxy-space coordinates (gx, gz).
     *
     * @param water the water body parameters
     * @param gx    galaxy-space X coordinate
     * @param gz    galaxy-space Z coordinate
     * @param time  elapsed simulation time in seconds
     * @return water surface height in local Y
     */
    public static float getHeight(WaterBodyComponent water, double gx, double gz, float time) {
        float height = water.baseHeight;
        for (int i = 0; i < water.waves.size; i++) {
            WaveParams w = water.waves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float dx = MathUtils.cos(dirRad);
            float dz = MathUtils.sin(dirRad);
            float phase = (float) (k * (dx * gx + dz * gz)) - k * w.speed * time;
            height += w.amplitude * MathUtils.sin(phase);
        }
        return height;
    }

    /**
     * Computes the displaced surface point and the surface normal at galaxy-space
     * coordinates (gx, gz) for the full Gerstner wave model.
     *
     * <p>The Gerstner model displaces the surface horizontally as well as vertically,
     * producing more realistic crests. The normal is computed analytically from
     * the partial derivatives of the displaced surface.
     *
     * @param water     the water body parameters
     * @param gx        galaxy-space X coordinate (undisplaced)
     * @param gz        galaxy-space Z coordinate (undisplaced)
     * @param time      elapsed simulation time in seconds
     * @param outPoint  receives the displaced surface point (x', y', z') in local space
     * @param outNormal receives the unit surface normal at that point
     */
    public static void getSurfacePointAndNormal(WaterBodyComponent water,
                                                 double gx, double gz, float time,
                                                 Vector3 outPoint, Vector3 outNormal) {
        float px = (float) gx;
        float py = water.baseHeight;
        float pz = (float) gz;

        // Partial derivatives for normal computation
        float ddx = 0f;
        float ddz = 0f;
        float dHorizXdx = 0f;
        float dHorizXdz = 0f;
        float dHorizZdx = 0f;
        float dHorizZdz = 0f;

        int waveCount = water.waves.size;
        for (int i = 0; i < waveCount; i++) {
            WaveParams w = water.waves.get(i);
            float amp = w.amplitude;
            float wl = w.wavelength;
            float speed = w.speed;
            float steep = w.steepness;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;

            float k = MathUtils.PI2 / wl;
            float dxDir = MathUtils.cos(dirRad);
            float dzDir = MathUtils.sin(dirRad);
            float phase = (float) (k * (dxDir * gx + dzDir * gz)) - k * speed * time;

            float sinP = MathUtils.sin(phase);
            float cosP = MathUtils.cos(phase);

            // Vertical displacement
            py += amp * sinP;

            // Horizontal Gerstner displacement
            float q = (k * amp * waveCount > 0f) ? steep / (k * amp * waveCount) : 0f;
            px -= q * amp * dxDir * cosP;
            pz -= q * amp * dzDir * cosP;

            // Derivatives for analytic normal
            ddx += k * amp * dxDir * cosP;
            ddz += k * amp * dzDir * cosP;

            dHorizXdx += q * amp * k * dxDir * dxDir * sinP;
            dHorizXdz += q * amp * k * dxDir * dzDir * sinP;
            dHorizZdx += q * amp * k * dzDir * dxDir * sinP;
            dHorizZdz += q * amp * k * dzDir * dzDir * sinP;
        }

        outPoint.set(px, py, pz);

        // Tangent vectors from partial derivatives:
        // T_x = (1 - dHorizXdx, ddx, -dHorizZdx)
        // T_z = (-dHorizXdz, ddz, 1 - dHorizZdz)
        // Normal = T_x x T_z
        float tx0 = 1f - dHorizXdx;
        float tx1 = ddx;
        float tx2 = -dHorizZdx;

        float tz0 = -dHorizXdz;
        float tz1 = ddz;
        float tz2 = 1f - dHorizZdz;

        outNormal.set(
            tx1 * tz2 - tx2 * tz1,
            tx2 * tz0 - tx0 * tz2,
            tx0 * tz1 - tx1 * tz0
        ).nor();

        // Ensure normal points upward
        if (outNormal.y < 0f) {
            outNormal.scl(-1f);
        }
    }
}
