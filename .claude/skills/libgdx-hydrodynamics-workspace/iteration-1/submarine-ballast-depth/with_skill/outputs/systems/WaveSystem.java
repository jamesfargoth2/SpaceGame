package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.WaveParams;

/**
 * Evaluates the Gerstner wave surface at arbitrary world points. Every other
 * water system queries this, so it runs at the highest priority among water
 * systems.
 *
 * <p>Wave phase uses <strong>galaxy-space</strong> (double) coordinates so
 * that waves tile seamlessly across floating-origin rebases. Callers must
 * convert local positions to galaxy-space before querying.
 *
 * <p>This system holds no entity family — it is a service system that other
 * water systems call into directly.
 */
public class WaveSystem extends EntitySystem {

    private final Array<WaveParams> activeWaves = new Array<>();
    private float baseHeight;
    private float time;

    /** Gravity constant; can be adjusted for alien planets. */
    private float gravity = 9.81f;

    /**
     * @param priority Ashley system priority. Should be lower (higher priority)
     *                 than BuoyancySystem and all other water systems.
     */
    public WaveSystem(int priority) {
        super(priority);
    }

    /**
     * Configures this system from a water body's parameters.
     *
     * @param waterBody the water body whose waves and base height to use
     */
    public void configure(WaterBodyComponent waterBody) {
        this.baseHeight = waterBody.baseHeight;
        this.activeWaves.clear();
        this.activeWaves.addAll(waterBody.waves);
    }

    /**
     * Sets the gravitational acceleration for this water body's planet.
     *
     * @param g gravitational acceleration in m/s²
     */
    public void setGravity(float g) {
        this.gravity = g;
    }

    public float getGravity() {
        return gravity;
    }

    public float getBaseHeight() {
        return baseHeight;
    }

    @Override
    public void update(float dt) {
        time += dt;
    }

    /**
     * Evaluates surface height at galaxy-space coordinates.
     *
     * @param gx galaxy-space X
     * @param gz galaxy-space Z
     * @return water surface height in local Y
     */
    public float getHeight(double gx, double gz) {
        float h = baseHeight;
        for (int i = 0; i < activeWaves.size; i++) {
            WaveParams w = activeWaves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float omega = k * w.speed;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float dx = MathUtils.cos(dirRad);
            float dz = MathUtils.sin(dirRad);
            float phase = (float) (k * (dx * gx + dz * gz)) - omega * time;
            h += w.amplitude * MathUtils.sin(phase);
        }
        return h;
    }

    /**
     * Computes the analytical surface normal from Gerstner partial derivatives.
     *
     * @param gx  galaxy-space X
     * @param gz  galaxy-space Z
     * @param out vector to write the result into
     * @return {@code out}, normalized
     */
    public Vector3 getNormal(double gx, double gz, Vector3 out) {
        float dhdx = 0f;
        float dhdz = 0f;
        for (int i = 0; i < activeWaves.size; i++) {
            WaveParams w = activeWaves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float dx = MathUtils.cos(dirRad);
            float dz = MathUtils.sin(dirRad);
            float phase = (float) (k * (dx * gx + dz * gz)) - k * w.speed * time;
            float dPhase = w.amplitude * k * MathUtils.cos(phase);
            dhdx += dPhase * dx;
            dhdz += dPhase * dz;
        }
        return out.set(-dhdx, 1f, -dhdz).nor();
    }

    /**
     * Computes Gerstner orbital velocity at a point, combined with ambient current.
     * Velocity attenuates exponentially with depth.
     *
     * @param gx      galaxy-space X
     * @param gz      galaxy-space Z
     * @param depth   depth below surface (positive = submerged)
     * @param current ambient current velocity
     * @param out     vector to write the result into
     * @return {@code out}
     */
    public Vector3 getFlowVelocity(double gx, double gz, float depth,
                                    Vector3 current, Vector3 out) {
        out.set(current);
        for (int i = 0; i < activeWaves.size; i++) {
            WaveParams w = activeWaves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float omega = k * w.speed;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float phase = (float) (k * (MathUtils.cos(dirRad) * gx
                         + MathUtils.sin(dirRad) * gz)) - omega * time;
            float depthAtten = (float) Math.exp(-k * Math.max(depth, 0f));
            out.x += w.amplitude * omega * MathUtils.cos(dirRad)
                     * MathUtils.cos(phase) * depthAtten;
            out.z += w.amplitude * omega * MathUtils.sin(dirRad)
                     * MathUtils.cos(phase) * depthAtten;
        }
        return out;
    }
}
